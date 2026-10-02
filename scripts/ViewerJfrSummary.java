import com.google.gson.*;
import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/** Developer-only JDK 17+ tool. Arguments: recording.jfr phase-log.jsonl output.json. */
class ViewerJfrSummary {
    static final class Window {
        String name; long start, end;
        Window(String name,long start,long end) {this.name=name;this.start=start;this.end=end;}
    }
    static final class Counts {
        long cpuSamples, allocationSamples, sampledAllocationBytes, gcPauseNanos, gcPauses;
        Map<String,Long> cpuThreads=new HashMap<>(), cpuOwners=new HashMap<>(), cpuLeaves=new HashMap<>();
        Map<String,Long> cpuDomains=new HashMap<>();
        Map<String,Long> allocationOwners=new HashMap<>(), allocationLeaves=new HashMap<>();
        Map<String,Long> indexCpuLeaves=new HashMap<>(), indexAllocationLeaves=new HashMap<>();
        Map<String,Long> renderCpuLeaves=new HashMap<>(), viewerRenderCpu=new HashMap<>();
        Map<String,Long> fileReadBytes=new HashMap<>(), monitorNanos=new HashMap<>();
    }
    static void add(Map<String,Long> map,String key,long amount) {map.merge(key,amount,Long::sum);}
    static String method(RecordedFrame f) {return f.getMethod().getType().getName()+"."+f.getMethod().getName();}
    static String owner(RecordedStackTrace trace) {
        if(trace!=null) for(RecordedFrame f:trace.getFrames())
            if(f.getMethod().getType().getName().startsWith("zsgrooms.replayviewer.")) return method(f);
        return "other";
    }
    static String domain(RecordedStackTrace trace) {
        if(trace!=null) for(RecordedFrame f:trace.getFrames()) {
            String name=f.getMethod().getType().getName();
            if(name.startsWith("net.minecraft.world.chunk.light.")) return "lighting";
            if(name.startsWith("net.minecraft.client.render.chunk.")) return "chunk-mesh";
            if(name.startsWith("org.lwjgl.")) return "graphics/native-call";
            if(name.startsWith("java.util.zip.Inflater")) return "decompression";
            if(name.startsWith("zsgrooms.replayviewer.")) return "viewer";
        }
        return "other";
    }
    static void count(Counts c,RecordedEvent e) {
        String type=e.getEventType().getName();
        RecordedStackTrace stack=e.getStackTrace();
        String leaf=stack==null||stack.getFrames().isEmpty()?"unknown":method(stack.getFrames().get(0));
        String owner=owner(stack);
        RecordedThread t=(type.equals("jdk.ExecutionSample")||type.equals("jdk.NativeMethodSample"))?e.getThread("sampledThread"):e.getThread();
        String thread=t==null||t.getJavaName()==null?"none":t.getJavaName();
        boolean index=thread.equals("ZSG replay milestones");
        boolean render=thread.equals("Render thread")||thread.equals("main");
        if(type.equals("jdk.ExecutionSample")||type.equals("jdk.NativeMethodSample")) {
            c.cpuSamples++;add(c.cpuThreads,thread,1);add(c.cpuOwners,owner,1);add(c.cpuLeaves,leaf,1);add(c.cpuDomains,domain(stack),1);
            if(index) add(c.indexCpuLeaves,leaf+" <- "+owner,1);
            if(render) {add(c.renderCpuLeaves,leaf,1); if(!owner.equals("other")) add(c.viewerRenderCpu,leaf+" <- "+owner,1);}
        } else if(type.equals("jdk.ObjectAllocationSample")) {
            long weight=e.getLong("weight");c.allocationSamples++;c.sampledAllocationBytes+=weight;
            add(c.allocationOwners,owner,weight);add(c.allocationLeaves,leaf+" <- "+owner,weight);
            if(index) add(c.indexAllocationLeaves,leaf+" <- "+owner,weight);
        } else if(type.equals("jdk.GCPhasePause")) {c.gcPauses++;c.gcPauseNanos+=e.getDuration().toNanos();}
        else if(type.equals("jdk.FileRead")) add(c.fileReadBytes,thread,e.getLong("bytesRead"));
        else if(type.equals("jdk.JavaMonitorEnter")) add(c.monitorNanos,thread+" "+leaf,e.getDuration().toNanos());
    }
    public static void main(String[] args) throws Exception {
        List<Window> windows=new ArrayList<>();Map<String,Long> starts=new HashMap<>();
        JsonParser parser=new JsonParser();
        for(String line:Files.readAllLines(Paths.get(args[1]))) {
            JsonObject row=parser.parse(line).getAsJsonObject();String kind=row.get("event").getAsString();
            String phase=row.get("phase").getAsString();long time=row.get("epochMillis").getAsLong();
            if(kind.equals("phase-start")) starts.put(phase,time);
            else if(kind.equals("phase-end")&&starts.containsKey(phase)) windows.add(new Window(phase,starts.remove(phase),time));
            else if(kind.equals("isolated-index")) windows.add(new Window(phase+"-index-"+row.get("trial").getAsInt(),
                    time-(long)row.get("wallMs").getAsDouble(),time));
        }
        Map<String,Counts> groups=new LinkedHashMap<>();groups.put("all",new Counts());
        for(Window w:windows) groups.put(w.name,new Counts());
        try(RecordingFile recording=new RecordingFile(Paths.get(args[0]))) {
            while(recording.hasMoreEvents()) {
                RecordedEvent e=recording.readEvent();count(groups.get("all"),e);
                long time=e.getStartTime().toEpochMilli();
                for(Window w:windows) if(time>=w.start&&time<w.end) count(groups.get(w.name),e);
            }
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("windows",windows);result.put("counts",groups);
        Files.writeString(Paths.get(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        for(Map.Entry<String,Counts> entry:groups.entrySet()) {
            Counts c=entry.getValue();System.out.printf(Locale.ROOT,"%s cpu=%d allocSamples=%d sampledAlloc=%.1fMiB GC=%.1fms%n",
                    entry.getKey(),c.cpuSamples,c.allocationSamples,c.sampledAllocationBytes/1048576.0,c.gcPauseNanos/1e6);
        }
    }
}
