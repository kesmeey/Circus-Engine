// Prepared probe. NOT executed until the user authorizes an original-game session.
// SHA-256 validation and isolated executable selection are enforced by the launcher.
'use strict';
if (Process.arch !== 'ia32') throw new Error('Expected DC3DD x86 process');
const mainModule=Process.enumerateModules().find(m=>/^DC3DD(?:_crack)?\.EXE$/i.test(m.name));
if(!mainModule)throw new Error('Reviewed DC3DD module not found');
const base = mainModule.base;
const at = va => base.add(va - 0x400000);
let sequence = 0;
let previous = null;
let previousScript = null;
let previousScene = null;
function hexBlock(address,length){return Array.from(new Uint8Array(address.readByteArray(length))).map(v=>v.toString(16).padStart(2,'0')).join('');}
function sceneState(engine,state){
  const objects=[];
  for(const index of [0,10,20,30,40,50,62,63,75,76]){
    const holder=engine.add(0x5778+4*index).readPointer();if(holder.isNull())continue;
    const object=holder.readPointer();if(object.isNull())continue;
    objects.push({index,values:[8,12,0x6c,0x70,0x74,0x7c,0x80].map(o=>object.add(o).readFloat()),header:hexBlock(object,8)});
  }
  return {objects,names:Array.from({length:43},(_,i)=>bytesZ(state.add(0x4c1c+i*48),48))};
}
Process.setExceptionHandler(details => {
  send({type:'native_exception',exception_type:details.type,address:details.address.toString(),memory:details.memory});
  return false; // Let the original exception handling run; never suppress game failures.
});
const user32=Process.enumerateModules().find(m=>m.name.toLowerCase()==='user32.dll');
if(user32) for(const suffix of ['A','W']) Interceptor.attach(user32.getExportByName('MessageBox'+suffix),{
  onEnter(args){try{send({type:'startup_message',wide:suffix==='W',text:suffix==='W'?args[1].readUtf16String():bytesZ(args[1],1024),caption:suffix==='W'?args[2].readUtf16String():bytesZ(args[2],256)});}catch(error){send({type:'probe_error',message:String(error)});}}
});
function bytesZ(address, limit) {
  const result=[];
  for(let i=0;i<limit;i++){const value=address.add(i).readU8();if(value===0)break;result.push(value);}
  return result.map(v=>v.toString(16).padStart(2,'0')).join('');
}
Interceptor.attach(at(0x42ae36), {
  onEnter() {
    if(sequence >= 10000) return;
    try {
      const state=at(0x4b7764).readPointer(), context=this.context;
      const values=[];for(let i=0;i<8;i++)values.push(context.esp.add(0x2100+4*i).readS32());
      const ordinary=new Uint8Array(state.add(0x90c4).readByteArray(1100));const changes=[];
      if(previous!==null)for(let i=0;i<ordinary.length;i++)if(ordinary[i]!==previous[i])changes.push([i,ordinary[i]]);
      previous=ordinary;
      const scriptName=bytesZ(state.add(0x4b7c),160);
      const initial=previousScript===null;
      if(previousScript!==scriptName){
        send({type:'script_state',sequence:sequence++,script_name_hex:scriptName,initial,
          ordinary_hex:hexBlock(state.add(0x90c4),1100),bank100_hex:hexBlock(state.add(0xcc),150),
          bank96_hex:hexBlock(state.add(0x174),400),bank97_hex:hexBlock(state.add(0x304),1200),bank98_hex:hexBlock(state.add(0x7b4),650),
          special_fields:[0xc,0x34,0x19414,0x196e8,0x196f0,0x196f4,0x1980c,0x5be8c].map(o=>[o,state.add(o).readS32()])});
        previousScript=scriptName;
      }
      const scene=sceneState(context.ebx,state),encoded=JSON.stringify(scene);
      if(encoded!==previousScene){send({type:'scene_state',sequence:sequence++,before_pc:context.ebx.add(0x5770).readS32(),script_name_hex:scriptName,...scene});previousScene=encoded;}
      send({type:'mes_dispatch',sequence:sequence++,opcode:context.edx.toUInt32(),
        script_name_hex:scriptName,pc:context.ebx.add(0x5770).readS32(),next_pc:state.add(0x55e8).readS32(),
        arguments_i32:values,string_prefix_hex:bytesZ(context.esp.add(0x100),128),ordinary_changes:changes});
    } catch(error) {send({type:'probe_error',message:String(error)});}
  }
});
for(const [name,va,count] of [['ui_register',0x417530,4],['script_load',0x4277c0,2],['named_jump',0x420c00,1]]) {
  Interceptor.attach(at(va),{onEnter(args){if(sequence>=10000)return;try{send({type:name,sequence:sequence++,args:Array.from({length:count},(_,i)=>args[i].toUInt32()),text_hex:bytesZ(args[name==='ui_register'?4:0],160)});}catch(error){send({type:'probe_error',message:String(error)});}}});
}
send({type:'probe_ready',module:mainModule.name,base:base.toString(),limit:10000,observation_only:true});
