// Observation only. Launcher enforces reviewed module hashes and duration.
'use strict';
const mod=Process.enumerateModules().find(m=>/^DC2DM(?:_chs_c3\.7s)?\.EXE$/i.test(m.name));
if(!mod||Process.arch!=='ia32')throw new Error('Expected reviewed x86 DM module');
const at=va=>mod.base.add(va-0x400000);let count=0,previous=null,lastScript='';
function hex(p,n){return Array.from(new Uint8Array(p.readByteArray(n))).map(x=>x.toString(16).padStart(2,'0')).join('');}
function z(p,n=160){let a=[];for(let i=0;i<n;i++){let b=p.add(i).readU8();if(!b)break;a.push(b);}return a.map(x=>x.toString(16).padStart(2,'0')).join('');}
function event(e){if(count<9600)send({sequence:count++,...e});}
Interceptor.attach(at(0x42cd1b),{onEnter(){if(count>=9600)return;try{
    const name=z(at(0xbe4080)),s=this.context.esp,ordinary=new Uint8Array(at(0xbe65b8).readByteArray(1100)),changes=[];
    if(previous)for(let i=0;i<1100;i++)if(previous[i]!==ordinary[i])changes.push([i,ordinary[i]]);
    if(lastScript!==name){event({type:'script_state',name,ordinary:hex(at(0xbe65b8),1100),persistent:hex(at(0x78f4b4),382),settings:hex(at(0x78f3d0),228),fields:[0xb8266c,0xb82774,0xc293a4,0xc293b4,0xbe6eec,0xbe6ef0].map(a=>[a,at(a).readS32()])});lastScript=name;}
    previous=ordinary;
    event({type:'dispatch',name,next:at(0xbe4640).readS32(),opcode:s.add(0x28).readS32(),args:[at(0xbeccd8).readS32(),at(0xbd08e4).readS32(),s.add(0x10).readS32(),s.add(0x14).readS32(),s.add(0x20).readS32(),s.add(0x24).readS32(),s.add(0x30).readS32(),s.add(0x2c).readS32()],string:z(s.add(0x40)),changes});
}catch(e){event({type:'error',message:String(e)});}}});
for(const [type,va,n,stringIndex] of [['button',0x41f610,4,4],['script_load',0x428eb0,2,0],['label',0x424470,1,0],['image_load',0x407570,3,0]])
Interceptor.attach(at(va),{onEnter(args){if(count>=9600)return;try{event({type,args:Array.from({length:n},(_,i)=>args[i].toInt32()),text:z(args[stringIndex])});}catch(e){event({type:'error',message:String(e)});}}});
event({type:'ready',module:mod.name,limit:9600,observation_only:true});
