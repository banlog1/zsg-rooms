"""Manually drawn pixel lettering. Standard library only; no generated imagery/fonts."""
from pathlib import Path
import struct
import zlib
from html import escape

OUT = Path(__file__).resolve().parent
SIZE = 64
GLYPHS = {
    'Z': ['1111111','1111111','0000011','0000110','0001100','0011000','0110000','1111111','1111111'],
    'S': ['0111111','1111111','1100000','1100000','0111110','0000011','0000011','1111111','1111110'],
    'G': ['0111111','1111111','1100000','1100000','1101111','1100011','1100011','1111111','0111110'],
}
SMALL = {
    'R': ['11110','10001','10001','11110','10100','10010','10001'],
    'O': ['01110','10001','10001','10001','10001','10001','01110'],
    'M': ['10001','11011','10101','10101','10001','10001','10001'],
    'S': ['01111','10000','10000','01110','00001','00001','11110'],
}

def letters(text, alphabet, x, y, sx, sy, gap):
    pixels = set()
    for letter in text:
        rows = alphabet[letter]
        for j, row in enumerate(rows):
            for i, value in enumerate(row):
                if value == '1':
                    for dx in range(sx):
                        for dy in range(sy):
                            pixels.add((x+i*sx+dx,y+j*sy+dy))
        x += len(rows[0])*sx+gap
    return pixels

def shifted(mask, dx, dy):
    return {(x+dx,y+dy) for x,y in mask}

def expand(mask):
    return {(x+dx,y+dy) for x,y in mask for dx,dy in [(0,0),(1,0),(-1,0),(0,1),(0,-1)]}

def compose(transparent=False):
    ops=[]
    def rect(x,y,w,h,color):
        ops.append((x,y,w,h,color))
    def mask(points,color):
        for y in sorted({p[1] for p in points}):
            xs=sorted(x for x,py in points if py==y)
            if not xs: continue
            start=last=xs[0]
            for x in xs[1:]+[10000]:
                if x != last+1:
                    rect(start,y,last-start+1,1,color)
                    start=x
                last=x
    if not transparent:
        rect(0,0,64,64,'#0d171e')
        rect(2,2,60,60,'#384b52')
        rect(3,3,58,58,'#192b34')
        rect(4,4,56,27,'#1c303a')
        rect(4,31,56,29,'#172830')
        # Discrete corner cuts and restrained frame highlights.
        for x,y in [(2,2),(61,2),(2,61),(61,61)]:
            rect(x,y,1,1,'#0d171e')
        rect(5,3,54,1,'#52656a')
        rect(5,60,54,1,'#102027')
    def word(points,top,bottom,highlight,depth):
        extrusion=set().union(*(shifted(points,d,d) for d in range(1,depth+1)))
        mask(expand(points|extrusion),'#081218')
        mask(extrusion,'#35494e' if top=='#edf0e6' else '#735129')
        low=min(y for x,y in points)
        high=max(y for x,y in points)
        mask({p for p in points if p[1]<(low+high)/2},top)
        mask({p for p in points if p[1]>=(low+high)/2},bottom)
        mask({(x,y) for x,y in points if (x,y-1) not in points},highlight)
    word(letters('ZSG',GLYPHS,7,9,2,3,4),'#edf0e6','#c4d2cf','#ffffff',3)
    word(letters('ROOMS',SMALL,5,44,2,2,1),'#efc56e','#d7a64e','#ffe4a3',2)
    return ops

def rgba(color):
    return tuple(int(color[i:i+2],16) for i in (1,3,5))+(255,)

def png(path, ops, scale):
    canvas=[[(0,0,0,0)]*SIZE for _ in range(SIZE)]
    for x,y,w,h,color in ops:
        for yy in range(max(0,y),min(SIZE,y+h)):
            for xx in range(max(0,x),min(SIZE,x+w)):
                canvas[yy][xx]=rgba(color)
    raw=bytearray()
    for row in canvas:
        line=b'\0'+b''.join(bytes(pixel)*scale for pixel in row)
        raw.extend(line*scale)
    def chunk(kind,data):
        return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'
    data+=chunk(b'IHDR',struct.pack('!2I5B',SIZE*scale,SIZE*scale,8,6,0,0,0))
    data+=chunk(b'IDAT',zlib.compress(bytes(raw),9))+chunk(b'IEND',b'')
    path.write_bytes(data)

def svg(path,ops):
    body='\n'.join(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{color}"/>' for x,y,w,h,color in ops)
    path.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="512" height="512" shape-rendering="crispEdges" role="img" aria-labelledby="title">\n<title id="title">ZSG Rooms — pixel lettering</title>\n'+body+'\n</svg>\n',encoding='utf-8')

if __name__=='__main__':
    for transparent in (False,True):
        ops=compose(transparent)
        stem='zsg-rooms-transparent' if transparent else 'zsg-rooms-square'
        svg(OUT/(stem+'.svg'),ops)
        for scale in (1,2,8):
            png(OUT/(stem+f'-{SIZE*scale}.png'),ops,scale)
    print('Saved editable SVGs and pixel-exact 64, 128, and 512 px PNGs to',OUT)
