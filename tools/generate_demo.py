"""Create original synthetic MES/CRX/CRM/PCK/XPCM fixtures, with no game assets."""
from pathlib import Path
import math, struct, zlib

ROOT=Path(__file__).resolve().parent.parent
ASSETS=ROOT/'runtime/android/assets/demo'

def words(op,*args):return bytes([op])+struct.pack('<4h',*args)
def short(op,a=0,b=0):return bytes([op,a,b])
def string(op,text):return bytes([op])+text.encode('utf-8')+b'\0'
def mes(code):return struct.pack('<IH',0,0xDCA0)+b'\0'+code

def crx(w,h,color,x=0,y=0):
    rows=bytearray()
    for row in range(h):
        rows.append(0);previous=(0,0,0)
        for col in range(w):
            current=color(col,row);rows.extend((a-b)&255 for a,b in zip(current,previous));previous=current
    return b'CRXG'+struct.pack('<hhHHHHhH',x,y,w,h,3,0,0,0)+struct.pack('<I',0)+zlib.compress(rows)

def pck(items):
    count=len(items);offset=4+count*72;small=bytearray();large=bytearray();payload=bytearray()
    for name,data in items:
        small+=struct.pack('<II',offset,len(data));large+=name.encode('ascii').ljust(56,b'\0')+struct.pack('<II',offset,len(data));payload+=data;offset+=len(data)
    return struct.pack('<I',count)+small+large+payload

def generate():
    ASSETS.mkdir(parents=True,exist_ok=True)
    code=string(0x44,'dawn.crx')+string(0x46,'tone.pcm')
    code+=string(0x45,'CIRCUS Core\n这是独立创作的核心测试场景。\n脚本、图像和声音均不含原作内容。')+words(0x68,0,0,0,0)
    code+=words(0x7c,3,1,255,0)+words(0x7c,3,1,1,1)
    code+=words(0x80,3,1,0,0)+short(2,1)+string(0x45,'字节变量 255 + 1 = 0，条件分支已通过。\n请选择一条路，随后试试保存与读取。')+short(0x1f,1,1)
    for label,rect in [('蓝色小路',(100,250,470,330)),('金色小路',(550,250,920,330))]:code+=words(0x6c,*rect)+string(0x50,label)
    code+=words(0x69,0,0,0,0)
    for index,(label,scene,text) in enumerate([('蓝色小路','blue.crx','你沿着蓝色小路前行。'),('金色小路','gold.crx','你沿着金色小路前行。')]):
        code+=string(0x47,label)+words(0x7c,3,2,index+1,0)+string(0x44,scene)+words(0x6a,250,0,0,0)
        code+=string(0x45,text+'\n现在保存，再重启场景并读取，可回到这里。')+words(0x68,0,0,0,0)+string(0x4d,'arrival')+string(0x4c,'ending')
    code+=short(0)
    (ASSETS/'main.mes').write_bytes(mes(code))
    ending=string(0x47,'arrival')+string(0x45,'抵达终点。\n点击、选择、变量、计时、跨脚本入口和保存恢复\n由同一套核心处理。')+words(0x68,0,0,0,0)+short(0)
    (ASSETS/'ending.mes').write_bytes(mes(ending))
    def native_string(op,text):
        raw=text.encode('cp932')
        return bytes([op])+bytes((((36 if b==32 else b)-32)&255) for b in raw)+b'\0'
    # Authentic DD version/opcodes, entirely self-authored text. Not original story data.
    dialogue=native_string(0x64,'テスト')+native_string(0x65,'Page 1. Native DD text and click wait.\nSave here, advance, then load to restore this page.')+short(6)
    dialogue+=native_string(0x65,'Page 2. Display completion continues without another click.')
    dialogue+=native_string(0x65,'Page 3. The implicit boundary kept the next instruction intact.\nClick to finish the native dialogue test.')+short(6)+short(0)
    (ASSETS/'native-dialogue.mes').write_bytes(struct.pack('<IH',0,0xa5a8)+b'\0'+dialogue)
    colors={'dawn.crx':lambda x,y:(min(255,70+y//5),min(255,30+x//9),min(255,35+y//4)),
            'blue.crx':lambda x,y:(min(255,80+y//3),min(255,40+x//11),25),
            'gold.crx':lambda x,y:(25,min(255,45+x//7),min(255,80+y//3))}
    for name,color in colors.items():(ASSETS/name).write_bytes(crx(512,288,color))
    hz=22050;pcm=b''.join(struct.pack('<h',int(2500*math.sin(2*math.pi*440*i/hz)*(1-i/(hz//3)))) for i in range(hz//3))
    tone=b'XPCM'+struct.pack('<IIHHIIHH',len(pcm),0,1,1,hz,hz*2,2,16)+pcm
    (ASSETS/'tone.pcm').write_bytes(tone)
    (ASSETS/'sample.pck').write_bytes(pck([('tone.pcm',tone)]))
    # CRM with a base image and a small additive difference. Offsets stay archive-relative.
    base=crx(4,3,lambda x,y:(10,20,30));base_at=16+64;diff_at=base_at+len(base)
    diff=b'CRXD'+b'\0'*4+struct.pack('<I',base_at)+b'base.crx'.ljust(20,b'\0')+crx(2,1,lambda x,y:(1,2,3),1,1)
    header=b'CRXB'+struct.pack('<III',0,2,0)
    table=struct.pack('<II',base_at,0)+b'base.crx'.ljust(24,b'\0')+struct.pack('<II',diff_at,0)+b'diff.crx'.ljust(24,b'\0')
    (ASSETS/'sample.crm').write_bytes(header+table+base+diff)

if __name__=='__main__':generate()
