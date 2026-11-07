package org.circus.core;
import java.io.*;
import java.util.*;
/** Actual-machine map projections: captured six actors, strict clock boundary and draw arguments. */
public final class Dc2MapTests {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static String hash(Dc2Map map,Variables v)throws IOException{StringBuilder b=new StringBuilder();for(Dc2Map.Sprite s:map.sprites(v)){if(b.length()!=0)b.append('\n');b.append(s.signature());}return Binary.sha256(b.toString().getBytes("UTF-8"));}
 private static byte[] save(Dc2Map m)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();m.save(new DataOutputStream(b));return b.toByteArray();}
 private static void sample(Dc2Map map,Variables v,Dc2Ui ui,int tick,int hover,int confirm,String expected,int[] frames,int[] collision)throws Exception{
  map.elapse(tick-map.elapsed);if(hover>=0){int[][] regions=map.regions();int found=-1;for(int n=0;n<ui.buttons.size();n++)if(ui.buttons.get(n).action.equals("@dc2map:"+hover))found=n;map.hover(ui,found);}
  if(confirm>=0){map.select(confirm);map.buttons(ui);map.hover(ui,0);}
  check(hash(map,v).equals(expected),"native draw arguments tick"+tick+" hover"+hover+" confirm"+confirm);
  for(int n=0;n<6;n++){check(map.frame(n)==frames[n],"original strict one-step atlas clock "+n);check(map.collision(n)==collision[n],"native initialization collision shift "+n);}
  byte[] saved=save(map);Dc2Map copy=new Dc2Map();copy.load(new DataInputStream(new ByteArrayInputStream(saved)));check(Arrays.equals(saved,save(copy))&&hash(copy,v).equals(expected),"full map owner state retains current draw journal");
 }
 public static int run()throws Exception{
  checks=0;
  { // island_unique
   Variables v=new Variables(new DemoHost(),Variables.Model.PC_BYTE);Dc2Map map=new Dc2Map();Dc2Ui ui=new Dc2Ui();
   v.write(5,0,1);v.write(5,10,0);v.write(5,50,0);
   v.write(5,1,1);v.write(5,11,1);v.write(5,51,0);
   v.write(5,2,1);v.write(5,12,2);v.write(5,52,0);
   v.write(5,3,1);v.write(5,13,3);v.write(5,53,0);
   v.write(5,4,1);v.write(5,14,4);v.write(5,54,0);
   v.write(5,5,1);v.write(5,15,5);v.write(5,55,0);
   v.write(5,21,1);map.open(v);map.buttons(ui);
   sample(map,v,ui,0,-1,-1,"bdfd07f10641e34802bdc50096e6cf694673942967abf14adf49be7bcea278a2",new int[]{0,0,0,0,0,0},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500,-1,-1,"bdfd07f10641e34802bdc50096e6cf694673942967abf14adf49be7bcea278a2",new int[]{0,0,0,0,0,0},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,501,-1,-1,"8b13d3c26640f33bd4c87251ba7046f2f1e404507994f266f36e20a830b81032",new int[]{1,1,1,1,1,1},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,1002,-1,-1,"c2954c0c8af06aa2bb318e668e9a67b70e463c83213191ea8a6bf605ee0e22a1",new int[]{2,2,2,2,2,2},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,-1,-1,"9a3ba41c9348d530ab53121a06db7c61a3bdf67e35634ab191009d58b7e08fc8",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,0,-1,"0c660c168c5c3f4dea7a3fe23975b90dcad07e540044b4923926c4ac729e3e82",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,1,-1,"1f04c79d6e8da935425d511498c39f553f404c3df736ab4a9190741b9a6faa1c",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,2,-1,"11328cb63d49144748572e80d632f5010124e28cee89169ce29fe772bafd0ab4",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,3,-1,"5ec753cc38396db37c70f323207b1e1d881d2c20900657c0dff8e646d9ac7139",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,4,-1,"318b806e4723646ba6be0a83b09593bc7156da1f5f019595bf8eec86b021115d",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,5,-1,"2c1ee8eab94e0de4ba5fba5ca45d3535ee475c787170b861039a397c2cdc50ed",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,-1,5,"cefd0bf2bef5496997b73f7d4f033596e0ad2a8b37f7e0faf0ee9073cb9b9e58",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   map.confirm(v);check(!map.active&&v.read(5,20)==5,"confirmed native location reaches byte5:20");
  }
  { // school_unique
   Variables v=new Variables(new DemoHost(),Variables.Model.PC_BYTE);Dc2Map map=new Dc2Map();Dc2Ui ui=new Dc2Ui();
   v.write(5,0,1);v.write(5,10,0);v.write(5,50,0);
   v.write(5,1,1);v.write(5,11,1);v.write(5,51,0);
   v.write(5,2,1);v.write(5,12,2);v.write(5,52,0);
   v.write(5,3,1);v.write(5,13,3);v.write(5,53,0);
   v.write(5,4,1);v.write(5,14,5);v.write(5,54,0);
   v.write(5,5,1);v.write(5,15,6);v.write(5,55,0);
   v.write(5,21,0);map.open(v);map.buttons(ui);
   sample(map,v,ui,0,-1,-1,"06e533359d0949d9d1a25d6f450d128ef53b419115be5e0c7a9c87e06fc42e13",new int[]{0,0,0,0,0,0},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500,-1,-1,"06e533359d0949d9d1a25d6f450d128ef53b419115be5e0c7a9c87e06fc42e13",new int[]{0,0,0,0,0,0},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,501,-1,-1,"1dbdd1fbbfe8d9613e0e2a7d5f7036485050d2342f69c37ed20724935074582a",new int[]{1,1,1,1,1,1},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,1002,-1,-1,"d6afd435a810062bf37865fa5dd8f2a3f2a68ac356a38459abdab4135aeae90a",new int[]{2,2,2,2,2,2},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,-1,-1,"b53d49bd83f8e3145ef1589a0ada550e687a09e8a9924ebb7d45ebe195f1ffb6",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,0,-1,"14c1e1a0733fc5f6c2c90d441274ec4448bc2404d407015b9f03d3f8a4d82e93",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,1,-1,"8de418be101dc73cf614adf192a7629757561173c6c775829c29bba961553a83",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,2,-1,"bac996ebaa9820025d470657b517ad768f9ae9a43d77e43a2a30b379110d0ace",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,3,-1,"367e9838e643a27045a04c9d5600f2f7f2f2eee9949ace8b798ca8a034238e5b",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,5,-1,"ef259a48455e329230eabc85d744c71d8b2c37a324e285d24bc06b97a2cbcd53",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,6,-1,"72d764408c0035c2dc51caafaa38e0fdef6d9b854944232c24c339d85724a9a1",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   sample(map,v,ui,500000,-1,6,"2a0f0f84b5a8a9d9547c5e685979f54d3a46154941619bef2c129a8c34a7a530",new int[]{3,3,3,3,3,3},new int[]{0,0,0,0,0,0});
   map.confirm(v);check(!map.active&&v.read(5,20)==6,"confirmed native location reaches byte5:20");
  }
  { // island_hidden
   Variables v=new Variables(new DemoHost(),Variables.Model.PC_BYTE);Dc2Map map=new Dc2Map();Dc2Ui ui=new Dc2Ui();
   v.write(5,0,1);v.write(5,10,2);v.write(5,50,0);
   v.write(5,1,0);v.write(5,11,99);v.write(5,51,0);
   v.write(5,2,1);v.write(5,12,4);v.write(5,52,0);
   v.write(5,3,0);v.write(5,13,99);v.write(5,53,0);
   v.write(5,4,1);v.write(5,14,1);v.write(5,54,0);
   v.write(5,5,0);v.write(5,15,99);v.write(5,55,0);
   v.write(5,21,1);map.open(v);map.buttons(ui);
   sample(map,v,ui,0,-1,-1,"fb5cb899cb5471191f46bf4e48c5e4654a6a48cc46510f02aff19545b23542de",new int[]{0,0,0,0,0,0},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500,-1,-1,"fb5cb899cb5471191f46bf4e48c5e4654a6a48cc46510f02aff19545b23542de",new int[]{0,0,0,0,0,0},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,501,-1,-1,"a32a96bd5f4c0104497244569139664606d63c48b28e6085438653dd64e34fc6",new int[]{1,1,1,1,1,1},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,1002,-1,-1,"5ef0732d20184acc17ebd2a7743dd6276a91e4bf75d73d1832e02334d442b392",new int[]{2,2,2,2,2,2},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,-1,-1,"76af7b7795164f2144cf84e7efaf45f6d77dfe63728bcf609dbd83bcc892b9c7",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,0,-1,"593027a00ca83b52167d9c7023a3bda56acbf94159abf7ce0f3de191945fbb51",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,1,-1,"8f9bfecad0db0cbb2d20075ec7469d49e1c9c033e5f4de08bad9a5d9438a397e",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,2,-1,"b4fca6eff624a536fa54826baf3ad61f6b8fbadb49ed1643bdb1fe5096efdce3",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,3,-1,"2d48dd09aef3116d596f5a958f466495e8d7022f9e60cc7d3242b5e0ba71525f",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,4,-1,"20a8896c27667f46772deb72539cb9ece49b54389861d27899c94e909b6a10ea",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,5,-1,"c0087c8d1580c549770eab4fa152267bf4966b46b76ee40035c9f4b985263774",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   sample(map,v,ui,500000,-1,5,"777622451128b95fae04b5a4f5a3e9494ec0e02bcc25259d1440955e9d66b40f",new int[]{3,3,3,3,3,3},new int[]{0,120,0,120,0,0});
   map.confirm(v);check(!map.active&&v.read(5,20)==5,"confirmed native location reaches byte5:20");
  }
  { // school_overlap
   Variables v=new Variables(new DemoHost(),Variables.Model.PC_BYTE);Dc2Map map=new Dc2Map();Dc2Ui ui=new Dc2Ui();
   v.write(5,0,1);v.write(5,10,0);v.write(5,50,0);
   v.write(5,1,1);v.write(5,11,0);v.write(5,51,1);
   v.write(5,2,1);v.write(5,12,2);v.write(5,52,0);
   v.write(5,3,1);v.write(5,13,2);v.write(5,53,1);
   v.write(5,4,1);v.write(5,14,5);v.write(5,54,0);
   v.write(5,5,1);v.write(5,15,6);v.write(5,55,1);
   v.write(5,21,0);map.open(v);map.buttons(ui);
   sample(map,v,ui,0,-1,-1,"9175b0f8815b254cc2812d4d05edd1b05513bca8ace0ec7757475f91546189a7",new int[]{0,0,0,0,0,0},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500,-1,-1,"9175b0f8815b254cc2812d4d05edd1b05513bca8ace0ec7757475f91546189a7",new int[]{0,0,0,0,0,0},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,501,-1,-1,"52657104fed46a677777fd353e47210411b94a9d31fed3bd31b3d77f7cb9a5df",new int[]{1,1,1,1,1,1},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,1002,-1,-1,"a18c0bc8485667531059bd0ef1350fc8fb9df2929c59ccb77b9a28b0b7789a62",new int[]{2,2,2,2,2,2},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,-1,-1,"04ad9372330322f18309b2fa2f89ea553cb5cd498c1dfac9d0226303306b5a27",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,0,-1,"45709173075dbb633e84460e4a7299c6dab531d7c91ca0078f5678d7c3f144b4",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,1,-1,"b5f9f959f3a273e5352075eae52cb4c7442c8594c52c2187eeecaf007c2c2513",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,2,-1,"56f976cb1e5fd779b23bc21ac671921addf046b0be8c2d43195eef93d1ea98bc",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,3,-1,"61d5562baf54d3a22ffe231b774749f299cf4b890454008368e2c84023464d68",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,5,-1,"3b11d489925972c616842c60e4b3c9cd3881aff4cc70712e3fdc595828ec7e32",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,6,-1,"2aad6d9d3649d8dc1d9ec1b495096abf5aa84886cfc1b105e3ade03f0d8e57cd",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   sample(map,v,ui,500000,-1,6,"8c33f5d8570c7a05fcff9ea830b3d9818826a231676ca39a88e4cc60ce35e97d",new int[]{3,3,3,3,3,3},new int[]{120,0,120,0,0,0});
   map.confirm(v);check(!map.active&&v.read(5,20)==6,"confirmed native location reaches byte5:20");
  }

  Variables v=new Variables(new DemoHost(),Variables.Model.PC_BYTE);Dc2Map map=new Dc2Map();Dc2Ui ui=new Dc2Ui();map.open(v);map.buttons(ui);check(!map.right(v)&&map.active,"native bare right ignored before500ms");map.elapse(499);check(!map.right(v),"native bare right still ignored at499ms");map.elapse(1);check(map.right(v)&&v.read(5,20)==99&&!map.active&&map.backdrop,"native bare right writes99 for MES inline rMenu");long frozen=map.elapsed;map.elapse(9999);check(map.elapsed==frozen,"inline menu retains static map backdrop");map.open(v);map.buttons(ui);map.select(2);check(map.right(v)&&map.selected==-1&&map.active,"right at confirmation cancels even before500ms");map.select(2);map.cancel();check(map.selected==-1,"No cancels the native confirmation");
  map.open(v);map.elapse(0x100000000L+499);check(!map.right(v),"native right gate uses uint32 time difference after clock wrap");map.elapse(1);check(map.right(v)&&v.read(5,20)==99,"native right gate reopens at500 after wrap");
  return checks;
 }
 public static void main(String[] args)throws Exception{System.out.println("Dc2MapTests "+run()+" PASS");}
}
