package org.circus.core;
import java.io.*;
import java.util.*;

/** Actual41A0B0 integer instructions supply independent callback/state hashes.
 *Required pixel/platform leaves are explicit fixtures in these control tests. */
public final class Pc1TransitionTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final int[] FIELDS={0xbb8434,0x94c698,0x430284,0x9cca8c,0x454cf0,0xbb842c,0x9d96bc,0x9d96c0};
    private static int[] ints(String s){String[] split=s.split(",");int[] v=new int[split.length];for(int n=0;n<v.length;n++)v[n]=Integer.parseInt(split[n]);return v;}
    private static byte[] save(Pc1Transition.Frame frame)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();frame.save(new DataOutputStream(out));return out.toByteArray();}
    private static class Fake implements Pc1Transition.Services {
        final int[] option;final Map<Integer,Integer> values=new HashMap<>();final List<String> trace=new ArrayList<>();int clock,pumps;Pc1Transition owner;boolean blocked;String fail;
        Fake(int[] option){this.option=option;clock=option[6];int[] v={0,0,7,99,option[0],option[1],13,-7};for(int n=0;n<FIELDS.length;n++)values.put(FIELDS[n],v[n]);values.put(Pc1Transition.BACKGROUND,option[4]!=0?2:9);}
        public int read(int at)throws IOException{Integer v=values.get(at);if(v==null)throw new IOException("unowned transition fixture global");return v;}
        public void write(int at,int value){values.put(at,value);}
        int[] state()throws IOException{int[] out=new int[FIELDS.length];for(int n=0;n<out.length;n++)out[n]=read(FIELDS[n]);return out;}
        void event(String text)throws IOException{StringBuilder s=new StringBuilder(text).append('@');int[] raw=state();for(int n=0;n<raw.length;n++){if(n!=0)s.append(',');s.append(raw[n]);}trace.add(s.toString());if(owner!=null)try{owner.requireBoundary();}catch(IOException expected){blocked=true;}if(fail!=null&&text.equals(fail))throw new IOException("required "+text);}
        public void fullClip()throws IOException{event("fullClip");}public void fillBlack()throws IOException{event("fill");}public void backgroundName()throws IOException{event("name");}
        public int allocateLikeFront()throws IOException{event("allocate");return option[10];}
        public void target(int h)throws IOException{event("target:"+h);}public void redraw()throws IOException{event("redraw");}
        public void feedback(int x,int y,int h,int ratio)throws IOException{event("feedback:"+x+","+y+","+h+","+ratio);}
        public void copy(int x,int y,int h)throws IOException{event("copy:"+x+","+y+","+h);}
        public void present()throws IOException{event("present");}public void free(int h)throws IOException{event("free:"+h);}
        public int tickCount()throws IOException{event("tick");int now=clock;clock+=option[5];return now;}
        public int asyncKeyState(int key)throws IOException{event("key:"+key);if(option[9]!=0)write(Pc1Transition.SPEED,2);return option[2];}
        public int pump()throws IOException{event("pump");pumps++;if(pumps==option[8]){write(Pc1Transition.SPEED,3);write(Pc1Transition.PROGRESS,19);write(Pc1Transition.X,-9);write(Pc1Transition.Y,21);}return pumps==option[7]?1:0;}
    }
    // BEGIN ACTUAL41A0B0 MACHINE GOLDENS
    private static final String[] VECTORS={
        "-1,0,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,0,13,-7|fdcebdf7c6d7e59be01783bb9235cbf84c97f2abdf06933f2a008df502b1d73b",
        "-1,0,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,0,13,-7|f60a40f032475d3a32d62ebfc29ee565aa2d8c767ccd8b2d5295419664d20540",
        "-1,0,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,0,13,-7|fdcebdf7c6d7e59be01783bb9235cbf84c97f2abdf06933f2a008df502b1d73b",
        "-1,0,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,0,13,-7|f60a40f032475d3a32d62ebfc29ee565aa2d8c767ccd8b2d5295419664d20540",
        "-1,0,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,0,13,-7|9f159666fbb875178d49f69a06da02a246acf5d1bfea972ca06a5435e27b0e40",
        "-1,0,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,0,13,-7|d6e2ba75ecaeec298bdb908aa19172c8351a99dbadc596920cc0457b19a2c5cd",
        "-1,1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,1,13,-7|9129f5a5f922f550d34d32d9086a9b5e0978be66fea5dea9e121f790eb860d16",
        "-1,1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,1,13,-7|3841f6d46cf5793e2e4bd022ca8ff3d964070b968da47a60f52744c58aa44ec3",
        "-1,1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,1,13,-7|9129f5a5f922f550d34d32d9086a9b5e0978be66fea5dea9e121f790eb860d16",
        "-1,1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,1,13,-7|3841f6d46cf5793e2e4bd022ca8ff3d964070b968da47a60f52744c58aa44ec3",
        "-1,1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,1,13,-7|9129f5a5f922f550d34d32d9086a9b5e0978be66fea5dea9e121f790eb860d16",
        "-1,1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,1,13,-7|3841f6d46cf5793e2e4bd022ca8ff3d964070b968da47a60f52744c58aa44ec3",
        "-1,-1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,-1,13,-7|e3328b5e955fe121324e862962b23ba1b50185bd0ded8dab40f3e6976d8b3f34",
        "-1,-1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,-1,13,-7|c080ca8e95ecd391c607e337e029532ac03646d33cd6df6e21251fe385b25c12",
        "-1,-1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,-1,13,-7|e3328b5e955fe121324e862962b23ba1b50185bd0ded8dab40f3e6976d8b3f34",
        "-1,-1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,-1,13,-7|c080ca8e95ecd391c607e337e029532ac03646d33cd6df6e21251fe385b25c12",
        "-1,-1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,-1,13,-7|e3328b5e955fe121324e862962b23ba1b50185bd0ded8dab40f3e6976d8b3f34",
        "-1,-1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,-1,-1,13,-7|c080ca8e95ecd391c607e337e029532ac03646d33cd6df6e21251fe385b25c12",
        "0,0,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|6b116f452ba66c755c9818ddb16fcdb0f68c5fce18615edc5acd6668f75df981",
        "0,0,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|06af4454c4923b04e897ed4364cee5ade18fa7fb78cc25a717980b6afd471810",
        "0,0,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|6b116f452ba66c755c9818ddb16fcdb0f68c5fce18615edc5acd6668f75df981",
        "0,0,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|06af4454c4923b04e897ed4364cee5ade18fa7fb78cc25a717980b6afd471810",
        "0,0,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|a726f984386389acbb4629c8542dfe9970d33c7391e67fd2b839e4fafca70a19",
        "0,0,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|edef4a6d699136416f13ed122823afe58689d42ea564606ac732fe074f5745dc",
        "0,1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,1,13,-7|60d340306de9944f4099006e268dbe4599ad359b1409b4eed149e04d2a7a6b99",
        "0,1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,1,13,-7|4806ed46f33fab343755e77bf014b9cdecd0ea19d8ff5a956a46d472871b2e29",
        "0,1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,1,13,-7|60d340306de9944f4099006e268dbe4599ad359b1409b4eed149e04d2a7a6b99",
        "0,1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,1,13,-7|4806ed46f33fab343755e77bf014b9cdecd0ea19d8ff5a956a46d472871b2e29",
        "0,1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,1,13,-7|60d340306de9944f4099006e268dbe4599ad359b1409b4eed149e04d2a7a6b99",
        "0,1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,1,13,-7|4806ed46f33fab343755e77bf014b9cdecd0ea19d8ff5a956a46d472871b2e29",
        "0,-1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,-1,13,-7|383430afc289fe3c8f1ad13e5f2b48bf1b2e5c005a946c48a470b5a465fe316f",
        "0,-1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,-1,13,-7|6352b6bba93a3a04f6a97af33e3a0531f816dc3421bdd1b80f8e50f29b6b3fd8",
        "0,-1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,-1,13,-7|383430afc289fe3c8f1ad13e5f2b48bf1b2e5c005a946c48a470b5a465fe316f",
        "0,-1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,-1,13,-7|6352b6bba93a3a04f6a97af33e3a0531f816dc3421bdd1b80f8e50f29b6b3fd8",
        "0,-1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,-1,13,-7|383430afc289fe3c8f1ad13e5f2b48bf1b2e5c005a946c48a470b5a465fe316f",
        "0,-1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,0,-1,13,-7|6352b6bba93a3a04f6a97af33e3a0531f816dc3421bdd1b80f8e50f29b6b3fd8",
        "1,0,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|a82e2a1f0230c2a3402272bcd654abeb0529e48b8bf87b6e9e5982c9b1f1a1f9",
        "1,0,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|7534e955776e044c2b2a25f87532240e019ef959c040b9cb54f237639e7ed13e",
        "1,0,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|a82e2a1f0230c2a3402272bcd654abeb0529e48b8bf87b6e9e5982c9b1f1a1f9",
        "1,0,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|7534e955776e044c2b2a25f87532240e019ef959c040b9cb54f237639e7ed13e",
        "1,0,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|f48acdbc65a6b99a7e65ab97ea8148e449e5ee7671b7972afa77730a935956bb",
        "1,0,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|57eade4d966578885171f38e34d6837bd0944fd8e26c794a12e146a4fb2fd803",
        "1,1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,1,13,-7|83d971e28943fa1ab59e9f4666e46e109ec69179889129e55771b64ba3a4d550",
        "1,1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,1,13,-7|9b57e492cdb4b6dbefa47ce081567efd3a624ecfea62284fe8c3fea7276ca7fb",
        "1,1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,1,13,-7|83d971e28943fa1ab59e9f4666e46e109ec69179889129e55771b64ba3a4d550",
        "1,1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,1,13,-7|9b57e492cdb4b6dbefa47ce081567efd3a624ecfea62284fe8c3fea7276ca7fb",
        "1,1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,1,13,-7|83d971e28943fa1ab59e9f4666e46e109ec69179889129e55771b64ba3a4d550",
        "1,1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,1,13,-7|9b57e492cdb4b6dbefa47ce081567efd3a624ecfea62284fe8c3fea7276ca7fb",
        "1,-1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,-1,13,-7|1df86adfda0ee3f2ad86c5516cc703f12bdcd2ed6d76c2186ac04624eccac965",
        "1,-1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,-1,13,-7|78ab1d0ceab972cca55143eb47d0493a852715f90bb97f240e81c9d0ecc2ed4b",
        "1,-1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,-1,13,-7|1df86adfda0ee3f2ad86c5516cc703f12bdcd2ed6d76c2186ac04624eccac965",
        "1,-1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,-1,13,-7|78ab1d0ceab972cca55143eb47d0493a852715f90bb97f240e81c9d0ecc2ed4b",
        "1,-1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,-1,13,-7|1df86adfda0ee3f2ad86c5516cc703f12bdcd2ed6d76c2186ac04624eccac965",
        "1,-1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,1,-1,13,-7|78ab1d0ceab972cca55143eb47d0493a852715f90bb97f240e81c9d0ecc2ed4b",
        "2,0,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|b55653664451bdd75c2c5abd6154d6730df285f34d7017a6d078b1da780af5e5",
        "2,0,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|0a9395df35e8744c820eaeb91b2e56bb1f45ae4bc6631592837f1d8b841a2421",
        "2,0,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|b55653664451bdd75c2c5abd6154d6730df285f34d7017a6d078b1da780af5e5",
        "2,0,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|0a9395df35e8744c820eaeb91b2e56bb1f45ae4bc6631592837f1d8b841a2421",
        "2,0,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|f8bd2c8a38f43a6bed4ecdb7dca389b79673f7307ff2b21ab492c3bfaa4d6352",
        "2,0,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|c95a756ccf92d9e060fb66fa8e4ba561103c063904b33f0f25597c8d4ec4db28",
        "2,1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,1,13,-7|df83a4b062aa40f613d81c9858a687d3e0a7a42f95a95dae1a88fdf3ecc5706c",
        "2,1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,1,13,-7|96b123705eb56542e9a581ad393816da7d73f9cc34ed70b80ab5831bb2d28bcf",
        "2,1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,1,13,-7|df83a4b062aa40f613d81c9858a687d3e0a7a42f95a95dae1a88fdf3ecc5706c",
        "2,1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,1,13,-7|96b123705eb56542e9a581ad393816da7d73f9cc34ed70b80ab5831bb2d28bcf",
        "2,1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,1,13,-7|df83a4b062aa40f613d81c9858a687d3e0a7a42f95a95dae1a88fdf3ecc5706c",
        "2,1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,1,13,-7|96b123705eb56542e9a581ad393816da7d73f9cc34ed70b80ab5831bb2d28bcf",
        "2,-1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,-1,13,-7|1bd7c437a85163eaa36f9d85097d0a3cc7ec52387e154b98b420882bdf8e4cb4",
        "2,-1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,-1,13,-7|ae31f6591befb4a421d0e7f7bf05131e1d79c028263dc92de05d80baf9e4ec39",
        "2,-1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,-1,13,-7|1bd7c437a85163eaa36f9d85097d0a3cc7ec52387e154b98b420882bdf8e4cb4",
        "2,-1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,-1,13,-7|ae31f6591befb4a421d0e7f7bf05131e1d79c028263dc92de05d80baf9e4ec39",
        "2,-1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,-1,13,-7|1bd7c437a85163eaa36f9d85097d0a3cc7ec52387e154b98b420882bdf8e4cb4",
        "2,-1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,2,-1,13,-7|ae31f6591befb4a421d0e7f7bf05131e1d79c028263dc92de05d80baf9e4ec39",
        "3,0,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|57698ff629ce8d88978030c74e65d2158d8d528a99bc0f9a15b57bd2f49ac39c",
        "3,0,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|b3be7a05cb2cf0c01b73c4d448ec766ff679dddab4aff0b64f8045839d53c46f",
        "3,0,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|57698ff629ce8d88978030c74e65d2158d8d528a99bc0f9a15b57bd2f49ac39c",
        "3,0,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|b3be7a05cb2cf0c01b73c4d448ec766ff679dddab4aff0b64f8045839d53c46f",
        "3,0,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|57698ff629ce8d88978030c74e65d2158d8d528a99bc0f9a15b57bd2f49ac39c",
        "3,0,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|b3be7a05cb2cf0c01b73c4d448ec766ff679dddab4aff0b64f8045839d53c46f",
        "3,1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,1,13,-7|77cb31276d7ee62ebee11e7d2a64deb9da26e12e121cfe8e6cd5be8dea4e05d5",
        "3,1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,1,13,-7|3f0febd7451ce9b2eab07118f2120ba0952b1a8c3a12239b2d811c2e275ec7c7",
        "3,1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,1,13,-7|77cb31276d7ee62ebee11e7d2a64deb9da26e12e121cfe8e6cd5be8dea4e05d5",
        "3,1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,1,13,-7|3f0febd7451ce9b2eab07118f2120ba0952b1a8c3a12239b2d811c2e275ec7c7",
        "3,1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,1,13,-7|77cb31276d7ee62ebee11e7d2a64deb9da26e12e121cfe8e6cd5be8dea4e05d5",
        "3,1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,1,13,-7|3f0febd7451ce9b2eab07118f2120ba0952b1a8c3a12239b2d811c2e275ec7c7",
        "3,-1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,-1,13,-7|f24548ad577a76ff2ae9d6d51eb1b2660976939fb30f18e3e29dfa3654ba202d",
        "3,-1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,-1,13,-7|7fab90a0494a4704a70ea91c0c119b9c491a3ba15597f972ddd5cbab59267034",
        "3,-1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,-1,13,-7|f24548ad577a76ff2ae9d6d51eb1b2660976939fb30f18e3e29dfa3654ba202d",
        "3,-1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,-1,13,-7|7fab90a0494a4704a70ea91c0c119b9c491a3ba15597f972ddd5cbab59267034",
        "3,-1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,-1,13,-7|f24548ad577a76ff2ae9d6d51eb1b2660976939fb30f18e3e29dfa3654ba202d",
        "3,-1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,3,-1,13,-7|7fab90a0494a4704a70ea91c0c119b9c491a3ba15597f972ddd5cbab59267034",
        "4,0,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,0,13,-7|0638aac873e7ba6c76b0bc26bdc2209f684ecab1543d2fb232ad36fa3a6cb72d",
        "4,0,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,0,13,-7|24fd27340bfb93f06d0b325c9db76d48e63311cc63656c1bdc4d34a23a5b17de",
        "4,0,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,0,13,-7|0638aac873e7ba6c76b0bc26bdc2209f684ecab1543d2fb232ad36fa3a6cb72d",
        "4,0,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,0,13,-7|24fd27340bfb93f06d0b325c9db76d48e63311cc63656c1bdc4d34a23a5b17de",
        "4,0,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,0,13,-7|91e1030fa39b600561f7f62cde154970d39f9770aad188c2bdbfaadccd335d09",
        "4,0,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,0,13,-7|a5ef38e32afedea41f6d7e29dfbca4da33ba240eb44f7166a206b4f539779127",
        "4,1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,1,13,-7|5721c41872472a974f42425da75d34c4251e3f98db0e9a61444d1edc47ff3c7f",
        "4,1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,1,13,-7|f39e5d28b4d61a9c06e77e83db2e3ac76406066f26b0b1a500b6c4c2872b9f60",
        "4,1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,1,13,-7|5721c41872472a974f42425da75d34c4251e3f98db0e9a61444d1edc47ff3c7f",
        "4,1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,1,13,-7|f39e5d28b4d61a9c06e77e83db2e3ac76406066f26b0b1a500b6c4c2872b9f60",
        "4,1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,1,13,-7|5721c41872472a974f42425da75d34c4251e3f98db0e9a61444d1edc47ff3c7f",
        "4,1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,1,13,-7|f39e5d28b4d61a9c06e77e83db2e3ac76406066f26b0b1a500b6c4c2872b9f60",
        "4,-1,0,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,-1,13,-7|a6e8d2159e13d9a018e02910f26a07c33bf9768e82cae8d50dee3723cc789b98",
        "4,-1,0,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,-1,13,-7|7f341fc0946cf77eebf35319b0f835b1272ebdced04103e781cead69d64218bc",
        "4,-1,1,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,-1,13,-7|a6e8d2159e13d9a018e02910f26a07c33bf9768e82cae8d50dee3723cc789b98",
        "4,-1,1,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,-1,13,-7|7f341fc0946cf77eebf35319b0f835b1272ebdced04103e781cead69d64218bc",
        "4,-1,32768,0,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,-1,13,-7|a6e8d2159e13d9a018e02910f26a07c33bf9768e82cae8d50dee3723cc789b98",
        "4,-1,32768,1,0,40,100,-1,-1,0,20,2|0|0,0,40,16,4,-1,13,-7|7f341fc0946cf77eebf35319b0f835b1272ebdced04103e781cead69d64218bc",
        "0,0,0,1,0,10,-16,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|f271ac9840e3d78fab908536d9e7500c39d8ff6f47ce2d6ffe1ff7ea5010f257",
        "0,0,0,1,0,40,100,1,-1,0,20,2|1|1,1,40,1,0,0,13,-7|40a6b35545ec1b4cc3ba79a6aeaef07f40476151f703e3cf1c9ab71140a9db5e",
        "0,0,0,1,0,40,100,3,-1,0,20,2|1|1,1,40,3,0,0,13,-7|f7c3d58280cee03258bf669f31afbd7093ffa53b636546c4a12f5684d21b6aaf",
        "0,0,0,1,0,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|d4d5bc3c92bd5d2c091d10bdd4227d7c8a4a4105f8b51dfb50e8406341f96e97",
        "0,0,0,1,0,40,100,-1,-1,1,20,2|0|0,0,40,16,2,0,13,-7|bb8eea7e47a8e89eec44716762fab3b8b5aeeb6e62aa0b6dead79f10bbc18787",
        "0,0,0,1,0,40,100,-1,-1,0,-1,2|0|0,0,40,16,0,0,13,-7|06af4454c4923b04e897ed4364cee5ade18fa7fb78cc25a717980b6afd471810",
        "0,0,0,1,1,10,-16,-1,-1,0,20,2|0|0,0,40,16,0,0,13,-7|cd49d33179f3bdc4e785523d3dca7fee91dde2105ac3a137b056e958c95210a1",
        "0,0,0,1,1,40,100,1,-1,0,20,2|1|1,1,40,1,0,0,13,-7|55114ead1439e7607f375f2f488977dc8e51f96d41b9d6777dfcf1d08ed1d3fa",
        "0,0,0,1,1,40,100,3,-1,0,20,2|1|1,1,40,3,0,0,13,-7|cb7b9585a99ce656fafcb64224d11888b6c02a8752fae3c993e3b4e0e0dbcbe6",
        "0,0,0,1,1,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|1f2c917eb2487700daa147c8632014430ca4218cb2bdf0d7d099ed0861d24072",
        "0,0,0,1,1,40,100,-1,-1,1,20,2|0|0,0,40,16,2,0,13,-7|f62c2af40122d2a4078df0f6f93fc2a9503e49ba8ccca02f2fd52fcc7eef0274",
        "0,0,0,1,1,40,100,-1,-1,0,-1,2|0|0,0,40,16,0,0,13,-7|a7eb2f8fa69a33b68ec5201eb39f34bffa0d4411a722480dc7a9e21047804cbc",
        "1,0,0,1,0,10,-16,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|8696c29319036ce19dda6d017eaf6d9d9fa850aac397013bc834fc9e5b667a74",
        "1,0,0,1,0,40,100,1,-1,0,20,2|1|1,1,40,2,1,0,13,-7|ed87d13cbfd14c146a1ccbafdba002fdd0e21c852bc338603dc2e50cbdcecb52",
        "1,0,0,1,0,40,100,3,-1,0,20,2|1|1,1,40,6,1,0,13,-7|626e099b0f2e7085eaa65be035f107174d19fb69a021f5ec9dee9ca45502944a",
        "1,0,0,1,0,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|de558bda5ac904f8bb7eee4f9a2ada254adf3391b18b0e5d4e444e6499cf19b4",
        "1,0,0,1,0,40,100,-1,-1,1,20,2|0|0,0,40,16,2,0,13,-7|cb33dfe312099dfb4e6970b26042be06ba8a7d68a4baf2dc86668c54ea7565b6",
        "1,0,0,1,0,40,100,-1,-1,0,-1,2|0|0,0,40,16,1,0,13,-7|7534e955776e044c2b2a25f87532240e019ef959c040b9cb54f237639e7ed13e",
        "1,0,0,1,1,10,-16,-1,-1,0,20,2|0|0,0,40,16,1,0,13,-7|aa7904cb2b0600ef218c01d7649907a03c15ebec20eaa6c94c5f44efeec5b60e",
        "1,0,0,1,1,40,100,1,-1,0,20,2|1|1,1,40,2,1,0,13,-7|0734d19d2fe47438dfe30485c9df8bbe8b2805e3c584b7a04aeb591ddaa66777",
        "1,0,0,1,1,40,100,3,-1,0,20,2|1|1,1,40,6,1,0,13,-7|9f7ae9359c8835d9df2755a2f85e6abe4625052b216619ccacaf12f6f5f2aae5",
        "1,0,0,1,1,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|c6a5d679ca63f24ac4aa0b91a07f241a1e31b296a3cce6edf8c36f56ba06b7b2",
        "1,0,0,1,1,40,100,-1,-1,1,20,2|0|0,0,40,16,2,0,13,-7|4ca3429be40b63908bc9611b1d6b4f8997089f05cfb12065090b6da94956b4ab",
        "1,0,0,1,1,40,100,-1,-1,0,-1,2|0|0,0,40,16,1,0,13,-7|7e7bb5cc7c5d90ca9e9e7f6c303aba8b255990f8f9d8ff8a1ba71c1bab3d2560",
        "2,0,0,1,0,10,-16,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|5fd77bc30ffa670693aa06a048986ef9410e5ba9378348c4e7c22957873204e4",
        "2,0,0,1,0,40,100,1,-1,0,20,2|1|1,1,40,4,2,0,13,-7|e8d87bc6836600caeca5fc935697cf7d754813c05609a417fa56e7987f1eb23f",
        "2,0,0,1,0,40,100,3,-1,0,20,2|1|1,1,40,12,2,0,13,-7|dc5bb26b8ab3594186c59c304657ebf1f980b0fa86a1bce729c18a2d9e899e9f",
        "2,0,0,1,0,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|09dea2dc33075069888fcf5e2b8b7959d52a6c910c88ca409054f2822c9723fc",
        "2,0,0,1,0,40,100,-1,-1,1,20,2|0|0,0,40,16,2,0,13,-7|0a9395df35e8744c820eaeb91b2e56bb1f45ae4bc6631592837f1d8b841a2421",
        "2,0,0,1,0,40,100,-1,-1,0,-1,2|0|0,0,40,16,2,0,13,-7|0a9395df35e8744c820eaeb91b2e56bb1f45ae4bc6631592837f1d8b841a2421",
        "2,0,0,1,1,10,-16,-1,-1,0,20,2|0|0,0,40,16,2,0,13,-7|94448bb62043d904e3b25e81105d96bbe6b4bdcf29af244d44d2ab16a8a114d3",
        "2,0,0,1,1,40,100,1,-1,0,20,2|1|1,1,40,4,2,0,13,-7|44649143740cf9e863133195cc6a94b7e234a8770182626ba074cd90568fccfc",
        "2,0,0,1,1,40,100,3,-1,0,20,2|1|1,1,40,12,2,0,13,-7|9161b8592a021266c9acf95f5eb42ce87e8587139beac38592c4a6c8ae8dfaed",
        "2,0,0,1,1,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|cdf35357f2fcf6d795d8de733df7344923a330fe3d158a3186e2d2991f4ea818",
        "2,0,0,1,1,40,100,-1,-1,1,20,2|0|0,0,40,16,2,0,13,-7|d2a56563f989628f599979afdcd609d03dd2400587fb691f45e670a12a04bc79",
        "2,0,0,1,1,40,100,-1,-1,0,-1,2|0|0,0,40,16,2,0,13,-7|af01b7ca5f01e3b399a22fc858d10ad4ba2c1f7ef84345b391532459f41cea8a",
        "3,0,0,1,0,10,-16,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|6e214312d5b32d1d66003da9f3ff46b7bfba08c37e75f9f1637864e82eead9e0",
        "3,0,0,1,0,40,100,1,-1,0,20,2|1|1,1,40,8,3,0,13,-7|e0f5bf6da46506f5f8709a2a6463e14976f3ae0e7a293746ec88bf5b9623b2fc",
        "3,0,0,1,0,40,100,3,-1,0,20,2|0|0,0,40,16,3,0,13,-7|b3be7a05cb2cf0c01b73c4d448ec766ff679dddab4aff0b64f8045839d53c46f",
        "3,0,0,1,0,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|332829107dbffa4716c691cd1fa4c7d2fc19d783ed2434c64115df7aac16af23",
        "3,0,0,1,0,40,100,-1,-1,1,20,2|0|0,0,40,16,3,0,13,-7|b3be7a05cb2cf0c01b73c4d448ec766ff679dddab4aff0b64f8045839d53c46f",
        "3,0,0,1,0,40,100,-1,-1,0,-1,2|0|0,0,40,16,3,0,13,-7|b3be7a05cb2cf0c01b73c4d448ec766ff679dddab4aff0b64f8045839d53c46f",
        "3,0,0,1,1,10,-16,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|d257dc8dbab8dfe4ba40f9f73f852029fc7624ecc62c117f86ce22404f9c297a",
        "3,0,0,1,1,40,100,1,-1,0,20,2|1|1,1,40,8,3,0,13,-7|27ed6996e7f960e36877b2e8b04af370805270284e5a4267dc3177c0377f15f5",
        "3,0,0,1,1,40,100,3,-1,0,20,2|0|0,0,40,16,3,0,13,-7|6d02e9944877878f734e755dc207a7bae0504a006961cdedaf3eb93b52ac054f",
        "3,0,0,1,1,40,100,-1,1,0,20,2|0|0,0,40,19,3,0,-9,21|b7a6e6402d6c808bf9f7f3805fb4d88b548992a854f5a493baaccabcb1f5c24c",
        "3,0,0,1,1,40,100,-1,-1,1,20,2|0|0,0,40,16,3,0,13,-7|6d02e9944877878f734e755dc207a7bae0504a006961cdedaf3eb93b52ac054f",
        "3,0,0,1,1,40,100,-1,-1,0,-1,2|0|0,0,40,16,3,0,13,-7|6bcb94abd45e6bdf1f943a6d015846cf4c4047aa2e1cad20553cc1b95d1c0ad0",
        "0,0,0,1,0,40,100,-1,-1,0,20,-1|0|0,0,7,99,0,0,13,-7|f77cf0f6353258b5773e5603f7b63f26cbc6b2c82508d17501316671a65053b9",
        "3,0,0,0,1,40,100,-1,-1,0,20,2|0|0,0,40,16,3,0,13,-7|6d02e9944877878f734e755dc207a7bae0504a006961cdedaf3eb93b52ac054f",
    };
    // END ACTUAL41A0B0 MACHINE GOLDENS
    private static void pixels()throws Exception{
        Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(font,null);int[] invalidates={0},clock={100},pumps={0};boolean[] blocked={false};
        Pc1GraphicsPipeline.Services leaves=(Pc1GraphicsPipeline.Services)java.lang.reflect.Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(proxy,method,args)->{if(method.getName().equals("invalidate")){invalidates[0]++;return null;}throw new IOException("unexpected pixel fixture service "+method.getName());});
        Pc1GraphicsPipeline graph=new Pc1GraphicsPipeline(f.host.pc1,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x123456)),Pc1GlyphCache.zeroBss(),font,f.backend,leaves);f.context=graph.render();f.initializeKnownScene();NativeRgbImage target=Pc1RenderContextTests.solid(64,48,0xabcdef);graph.render().compositor().installRgb(2,target);f.memory.writeInt(Pc1Transition.SPEED,3);
        Pc1TextServices.Platform platform=(Pc1TextServices.Platform)java.lang.reflect.Proxy.newProxyInstance(Pc1TextServices.Platform.class.getClassLoader(),new Class<?>[]{Pc1TextServices.Platform.class},(proxy,method,args)->{switch(method.getName()){case "tickCount":int now=clock[0];clock[0]+=40;return now;case "pumpMessages":pumps[0]++;try{f.host.save(new DataOutputStream(new ByteArrayOutputStream()));}catch(IOException required){blocked[0]=true;}return 0;default:throw new IOException("unexpected pixel platform leaf "+method.getName());}});
        Pc1Transition transfer=new Pc1Transition(f.host.pc1,graph,platform);check(transfer.begin(2,1)==Pc1TextPresentation.Transition.COMPLETE,"complete transition drives canonical graph and pump");
        check(Arrays.equals(target.bgr24(),graph.render().compositor().front().bgr24()),"exact final407880 copy reaches same whole persistent pixel buffer");check(pumps[0]==2&&invalidates[0]==3,"two immediate blends each retain final40ms pump before exact copy/present");check(blocked[0],"whole Host snapshot refused inside transition pump callback");check(f.memory.readInt(Pc1Transition.BUSY)==0&&f.memory.readInt(Pc1Transition.LOOP)==0&&f.memory.readInt(Pc1Transition.PROGRESS)==16,"successful native tail clears only its actual flags");
    }
    public static int run()throws Exception{
        checks=0;check(VECTORS.length==158,"all actual41A0B0 native control programs retained");
        for(String row:VECTORS){String[] fields=row.split("\\|");int[] o=ints(fields[0]);Fake f=new Fake(o);Pc1Transition transfer=new Pc1Transition(f);f.owner=transfer;Pc1Transition.Frame frame=transfer.beginFrame(o[11],o[3]);
            int limit=0;while(frame.active()){check(limit++<512,"finite native pump fixture");byte[] before=save(frame);frame=transfer.loadFrame(new DataInputStream(new ByteArrayInputStream(before)));check(Arrays.equals(before,save(frame)),"exact local-frame roundtrip without resource allocation");frame.step();}
            check(frame.result()==Integer.parseInt(fields[1]),"original native return value");check(Arrays.equals(f.state(),ints(fields[2])),"all canonical globals equal original instructions");
            check(Binary.sha256(String.join("\n",f.trace).getBytes("UTF-8")).equals(fields[3]),"every required callback sees same original ordered raw globals: "+Arrays.toString(o));check(f.blocked,"whole native callback is fenced");
        }
        int[] o={3,0,0,1,1,40,100,-1,-1,0,20,2};Fake f=new Fake(o);Pc1Transition transfer=new Pc1Transition(f);f.owner=transfer;Pc1Transition.Frame frame=transfer.beginFrame(2,1);f.fail="pump";boolean failed=false;try{frame.step();}catch(IOException required){failed=true;}check(failed&&f.read(Pc1Transition.BUSY)==1&&f.read(Pc1Transition.LOOP)==1,"required consumer failure preserves preceding native writes");failed=false;try{save(frame);}catch(IOException required){failed=true;}check(failed,"failed native continuation cannot be restored as a valid suspended frame");
        pixels();return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" complete transition checks");}
}
