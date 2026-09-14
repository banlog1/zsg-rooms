# Windows host sampling without requiring WMI/CIM permissions.
if (-not ('ZsgFilterHost' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class ZsgFilterHost {
    [StructLayout(LayoutKind.Sequential)]
    struct BasicLimits {
        public long processTime, jobTime;
        public uint flags;
        public UIntPtr minWorkingSet, maxWorkingSet;
        public uint activeProcesses;
        public UIntPtr affinity;
        public uint priority, scheduling;
    }
    [StructLayout(LayoutKind.Sequential)]
    struct IoCounters { public ulong readOps, writeOps, otherOps, readBytes, writeBytes, otherBytes; }
    [StructLayout(LayoutKind.Sequential)]
    struct JobLimits {
        public BasicLimits basic;
        public IoCounters io;
        public UIntPtr processMemory, jobMemory, peakProcessMemory, peakJobMemory;
    }
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode)]
    static extern IntPtr CreateJobObject(IntPtr attributes, string name);
    [DllImport("kernel32.dll")]
    static extern bool SetInformationJobObject(IntPtr job, int type, ref JobLimits limits, uint length);
    [DllImport("kernel32.dll")]
    static extern bool QueryInformationJobObject(IntPtr job, int type, out JobLimits limits, uint length, IntPtr returned);
    [DllImport("kernel32.dll")]
    static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr handle);
    public sealed class WorkerJob : IDisposable {
        IntPtr handle;
        public WorkerJob() {
            handle=CreateJobObject(IntPtr.Zero, null);
            JobLimits limits=new JobLimits();
            limits.basic.flags=0x2000 | 0x20; // Kill descendants on close; enforce below-normal priority.
            limits.basic.priority=0x4000;
            if (handle==IntPtr.Zero || !SetInformationJobObject(handle, 9, ref limits, (uint)Marshal.SizeOf(typeof(JobLimits)))) {
                Dispose(); throw new InvalidOperationException("Worker job setup failed.");
            }
        }
        public void Assign(System.Diagnostics.Process process) {
            if (!AssignProcessToJobObject(handle, process.Handle)) throw new InvalidOperationException("Worker isolation failed.");
        }
        public ulong PeakCommittedBytes() {
            JobLimits limits;
            if (!QueryInformationJobObject(handle, 9, out limits, (uint)Marshal.SizeOf(typeof(JobLimits)), IntPtr.Zero))
                throw new InvalidOperationException("Worker memory query failed.");
            return limits.peakJobMemory.ToUInt64();
        }
        public void Dispose() { if (handle!=IntPtr.Zero) { CloseHandle(handle); handle=IntPtr.Zero; } }
    }
    [StructLayout(LayoutKind.Sequential)]
    public struct Memory {
        public uint length, load;
        public ulong totalPhysical, availablePhysical, totalPage, availablePage, totalVirtual, availableVirtual, extended;
    }
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool GlobalMemoryStatusEx(ref Memory status);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool GetLogicalProcessorInformationEx(int relationship, IntPtr buffer, ref uint length);
    [DllImport("kernel32.dll")]
    static extern bool GetSystemTimes(out ulong idle, out ulong kernel, out ulong user);
    [DllImport("kernel32.dll")]
    static extern uint SetThreadExecutionState(uint flags);
    public static void KeepAwake(bool enabled) {
        if (SetThreadExecutionState(enabled ? 0x80000001u : 0x80000000u)==0)
            throw new InvalidOperationException("Could not update the temporary sleep request.");
    }
    public static Memory ReadMemory() {
        Memory m = new Memory(); m.length = (uint)Marshal.SizeOf(typeof(Memory));
        if (!GlobalMemoryStatusEx(ref m)) throw new InvalidOperationException("Host memory query failed.");
        return m;
    }
    public static int PhysicalCores() {
        uint length=0;
        GetLogicalProcessorInformationEx(0, IntPtr.Zero, ref length);
        if (length==0) throw new InvalidOperationException("Host CPU query failed.");
        IntPtr buffer=Marshal.AllocHGlobal((int)length);
        try {
            if (!GetLogicalProcessorInformationEx(0, buffer, ref length)) throw new InvalidOperationException("Host CPU query failed.");
            int offset=0, count=0;
            while (offset<length) {
                int size=Marshal.ReadInt32(buffer, offset+4);
                if (size<8 || offset+size>length) throw new InvalidOperationException("Invalid host CPU record.");
                count++; offset+=size;
            }
            return count;
        } finally { Marshal.FreeHGlobal(buffer); }
    }
    public static ulong[] CpuTimes() {
        ulong idle, kernel, user;
        if (!GetSystemTimes(out idle, out kernel, out user)) throw new InvalidOperationException("Host CPU times unavailable.");
        return new ulong[] { idle, kernel+user };
    }
}
'@
}

function Get-FilterHostCapacity {
    $memory = [ZsgFilterHost]::ReadMemory()
    $chrome = @(Get-Process chrome -ErrorAction SilentlyContinue)
    [pscustomobject]@{
        processor = (Get-ItemProperty 'HKLM:\HARDWARE\DESCRIPTION\System\CentralProcessor\0').ProcessorNameString.Trim()
        physicalCores = [ZsgFilterHost]::PhysicalCores()
        logicalProcessors = [Environment]::ProcessorCount
        totalMemoryGiB = [Math]::Round($memory.totalPhysical / 1GB, 2)
        availableMemoryGiB = [Math]::Round($memory.availablePhysical / 1GB, 2)
        chromeProcesses = $chrome.Count
        chromeWorkingSetGiB = [Math]::Round(($chrome | Measure-Object WorkingSet64 -Sum).Sum / 1GB, 2)
    }
}
