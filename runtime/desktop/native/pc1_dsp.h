/* Integer FFT core adapted from GARbro XPCM (morkt,2015-2016,MIT),
 *see XpcmTransform.java and THIRD_PARTY.md for its complete license.
 *Control/scaling/memory addresses are verified against current PC1 CHS
 *403480/404060/404140. This file does not execute a target PE function. */
#ifndef CIRCUS_PC1_DSP_H
#define CIRCUS_PC1_DSP_H
static uint64_t dspProduct(uint32_t a,uint32_t b){return(uint64_t)((int64_t)(int32_t)a*(int64_t)(int32_t)b);}
static uint32_t dspPeWord(Context *c,DWORD address){return*(volatile DWORD *)(c->dspPe+(address-0x42ab1cu));}
static void dspPeStore(Context *c,DWORD address,DWORD value){*(volatile DWORD *)(c->dspPe+(address-0x42ab1cu))=value;}
static uint32_t dspFftCoefficient(Context *c,uint32_t index){return dspPeWord(c,((index&1u)?0x42aca0u:0x42bca0u)+(index/2u)*4u);}
/*All data additions/subtractions are unsigned32 wrap. Signed64 products
 *enter through dspProduct; narrowing implements original SHRD lowDWORD. */
static void dspFft(Context *c,uint32_t a1,volatile uint32_t *a2,volatile uint32_t *a3){

            uint32_t v4 = 1u << a1;
            uint32_t v5 = 1;
            uint32_t v68 = 1;
            uint32_t v6 = 1u << a1;
            uint32_t v79 = 1u << a1;
            if (a1 >= 3)
            {
                uint32_t v7 = v4 >> 1;
                uint32_t v59 = a1 - 2;
                for (;;)
                {
                    uint32_t v63 = v4;
                    uint32_t v82 = dspFftCoefficient(c,2 * v5);
                    uint32_t v66 = v7;
                    uint32_t v8 = v7 >> 1;
                    uint32_t v62 = v7 >> 1;
                    uint32_t v80 = dspFftCoefficient(c,1 + 2 * v5);
                    uint32_t v77 = 0;
                    if (v6 > 0)
                    {
                        uint32_t v73 = 0; // within a2;
                        uint32_t v69 = v8; // within a2
                        uint32_t v75 = v7; // within a2;
                        uint32_t v9 = v8 + 1; // within a3
                        uint32_t v10 = v7 + v8;
                        uint32_t v64 = v9; // within a3
                        uint32_t v11 = 1; // within a3;
                        uint32_t v12 = v10 + 1; // within a3
                        uint32_t v71 = v10; // within a2
                        uint32_t v13 = v7 + 1; // within a3
                        do
                        {
                            uint32_t v14 = a2[v75];
                            uint32_t v15 = a2[v73];
                            uint32_t v16 = a3[v11 - 1] - a3[v13 - 1];
                            a2[v73] += a2[v75];
                            a3[v11 - 1] += a3[v13 - 1];
                            a2[v75] = v15 - v14;
                            a3[v13 - 1] = v16;
                            uint32_t v17 = a2[v13];
                            uint32_t v18 = a2[v11] - v17;
                            uint32_t v19 = a3[v11] - a3[v13];
                            a2[v11] += v17;
                            a3[v11] += a3[v13];
                            a2[v13] = (uint32_t)(((dspProduct(v18,v82)) >> 12)
                                            + ((dspProduct(v19,v80)) >> 12));
                            a3[v13] = (uint32_t)(((dspProduct(v19,v82)) >> 12)
                                            - ((dspProduct(v18,v80)) >> 12));
                            uint32_t v20 = a2[v69] - a2[v71];
                            uint32_t v21 = v64; // within a3
                            uint32_t v22 = a3[v64 - 1] - a3[v12 - 1];
                            a2[v69] += a2[v71];
                            a3[v21 - 1] += a3[v12 - 1];
                            a2[v71] = v22;
                            a3[v12 - 1] = 0u-v20;
                            uint32_t v23 = a2[v12];
                            uint32_t v24 = a2[v64] - v23;
                            uint32_t v25 = a3[v64] - a3[v12];
                            a2[v21] += v23;
                            a3[v21] += a3[v12];
                            a2[v12] = (uint32_t)(((dspProduct(v25,v82)) >> 12)
                                            - ((dspProduct(v24,v80)) >> 12));

                            a3[v12] = (uint32_t)-(int64_t)(((dspProduct(v24,v82)) >> 12)
                                                   + ((dspProduct(v25,v80)) >> 12));
                            v13 += v63;
                            v75 += v63;
                            v11 += v63;
                            v73 += v63;
                            v12 += v63;
                            v71 += v63;
                            v77 += v63;
                            v69 += v63;
                            v64 += v63;
                        }
                        while (v77 < v79);
                        v8 = v62;
                        v5 = v68;
                        v7 = v66;
                        v6 = 1u << a1;
                    }
                    if (v8 > 2)
                    {
                        uint32_t v70 = 2; // within a3
                        uint32_t v72 = v7 + 2; // within a3
                        uint32_t v74 = v8 + 2; // within a3
                        uint32_t v27 = 1 + 4 * v5; // within dword_43A358
                        uint32_t v60 = v8 - 2;
                        uint32_t v76 = v8 + 2 + v7;
                        do
                        {
                            uint32_t v83 = dspFftCoefficient(c,v27 - 1);
                            uint32_t v81 = dspFftCoefficient(c,v27);
                            uint32_t v78 = 0;
                            if (v6 > 0)
                            {
                                uint32_t v28 = v70; // within a3
                                uint32_t v29 = v72; // within a3
                                uint32_t v65 = v74; // within a3
                                uint32_t v85 = v76; // within a3
                                do
                                {
                                    uint32_t v31 = a2[v29];
                                    uint32_t v32 = a2[v28] - v31;
                                    uint32_t v33 = a3[v28] - a3[v29];
                                    a2[v28] += v31;
                                    a3[v28] += a3[v29];
                                    a2[v29] = (uint32_t)(((dspProduct(v32,v83)) >> 12)
                                                    + ((dspProduct(v33,v81)) >> 12));
                                    a3[v29] = (uint32_t)(((dspProduct(v33,v83)) >> 12)
                                                    - ((dspProduct(v32,v81)) >> 12));
                                    uint32_t v34 = a2[v65] - a2[v85];
                                    uint32_t v35 = a3[v65] - a3[v85];
                                    a2[v65] += a2[v85];
                                    a3[v65] += a3[v85];
                                    a2[v85] = (uint32_t)(((dspProduct(v35,v83)) >> 12)
                                                    - ((dspProduct(v34,v81)) >> 12));
                                    a3[v85] = (uint32_t)-(int64_t)(((dspProduct(v34,v83)) >> 12)
                                                           + ((dspProduct(v35,v81)) >> 12));
                                    v29 += v63;
                                    v85 += v63;
                                    v28 += v63;
                                    v65 += v63;
                                    v78 += v63;
                                }
                                while (v78 < v79);
                                v5 = v68;
                                v6 = 1u << a1;
                            }
                            v27 += 2 * v5;
                            ++v70;
                            ++v72;
                            ++v74;
                            ++v76;
                        }
                        while (v60-- != 1);
                    }
                    v68 = 2 * v5;
                    --v59;
                    if (0 == v59)
                        break;
                    v7 = v62;
                    v5 *= 2;
                    v4 = v66;
                }
            }
            if (!(a1 < 2 || v6 <= 0))
            {
                uint32_t v37 = 1; // within a2
                uint32_t v38 = 3; // within a3
                uint32_t v88 = (v6 + 3) >> 2;
                do
                {
                    uint32_t v39 = a2[v37 - 1];
                    uint32_t v40 = a2[v37 + 1];
                    uint32_t v41 = a3[v38 - 3] - a3[v38 - 1];
                    a2[v37 - 1] = v40 + v39;
                    v37 += 4;
                    a3[v38 - 3] += a3[v38 - 1];
                    a2[v37 - 3] = v39 - v40;
                    a3[v38 - 1] = v41;
                    uint32_t v42 = a2[v38];
                    uint32_t v43 = a3[v38 - 2] - a3[v38];
                    v38 += 4;
                    uint32_t v44 = a2[v37 - 4] - v42;
                    a2[v37 - 4] += v42;
                    a3[v38 - 6] += a3[v38 - 4];
                    a2[v38 - 4] = v43;
                    a3[v38 - 4] = 0u-v44;
                    --v88;
                }
                while (0 != v88);
                v6 = v79;
            }
            uint32_t v45 = 0; // within a2;
            if (v6 > 0)
            {
                uint32_t v47 = 1; // within a3
                uint32_t v89 = (v79 + 1) >> 1;
                do
                {
                    uint32_t v48 = a2[v47];
                    uint32_t v49 = a3[v47 - 1] - a3[v47];
                    uint32_t v50 = a2[v45] - v48;
                    v47 += 2;
                    a2[v45] += v48;
                    v45 += 2;
                    a3[v47 - 3] += a3[v47 - 2];
                    a2[v47 - 2] = v50;
                    a3[v47 - 2] = v49;
                    --v89;
                }
                while (0 != v89);
                v45 = 0; // within a2;
                v6 = v79;
            }
            uint32_t v51 = 0;
            uint32_t result = v6 / 2;
            uint32_t v67 = v6 / 2;
            uint32_t v90 = 1;
            if ( v6 - 1 > 1 )
            {
                uint32_t v54 = 1; // within a3
                for (;;)
                {
                    for ( ; result <= v51; result /= 2)
                        v51 -= result;
                    v51 += result;
                    if (v90 < v51)
                    {
                        uint32_t v55 = a2[v45 + v51];
                        a2[v45 + v51] = a2[v54];
                        a2[v54] = v55;
                        uint32_t v56 = a3[v51];
                        a3[v51] = a3[v54];
                        a3[v54] = v56;
                    }
                    ++v54;
                    result = v79 - 1;
                    ++v90;
                    if (v90 >= v79 - 1)
                        break;
                    result = v67;
                }
                v6 = v79;
            }

}

static void dspSelectCoefficients(Context *c,DWORD extra){DWORD n,source=0x42ab60u+(extra&15u)*64u;for(n=0;n<16;n++)dspPeStore(c,0x42ab20u+n*4u,dspPeWord(c,source+n*4u));}
static volatile BYTE *dspPointer(Context *c,DWORD address,DWORD *length){
    static const DWORD base[]={0x455574u,0xbc5f80u,0x42fca8u,0x43de78u,0xbc1d78u,0xbc1d7cu,0xbc1d88u,0xbc1da4u,0xbc3da4u,0xbc5da4u};DWORD n,total;BYTE *pointer;
    if(c->physicalBound)return physicalPointer(c,address,1,1,length);
    if(c->dspPe&&address-0x42ab1cu<c->dspPeLength){*length=c->dspPeLength-(address-0x42ab1cu);return c->dspPe+(address-0x42ab1cu);}
    for(n=0;n<sizeof(base)/sizeof(base[0]);n++){pointer=dspRegion(c,base[n],&total);if(address-base[n]<total){*length=total-(address-base[n]);return pointer+(address-base[n]);}}
    c->dspFaultAddress=address;*length=0;return NULL;
}
static int dspByte(Context *c,DWORD address,BYTE *value){DWORD length;volatile BYTE *pointer=c->physicalBound?physicalPointer(c,address,1,1,NULL):dspPointer(c,address,&length);if(!pointer)return 0;*value=*pointer;return 1;}
static int dspByteStore(Context *c,DWORD address,BYTE value){DWORD length;volatile BYTE *pointer=c->physicalBound?physicalPointer(c,address,1,2,NULL):dspPointer(c,address,&length);if(!pointer)return 0;*pointer=value;return 1;}
typedef int (*DspRead)(Context *,void *,DWORD,DWORD,DWORD,DWORD);
#define LZWORD(c,offset) (*(volatile DWORD *)((c)->dspLz+(offset)))
/*403E40 clears only these six words. Ring, buffered input and source pointer
 *are deliberately stale; the first403DF0 refill owns the pointer store. */
static void dspInitLz(Context *c,DWORD packed){LZWORD(c,0x10004)=0;LZWORD(c,0x14024)=0;LZWORD(c,0x14020)=packed;LZWORD(c,0x1402c)=0;LZWORD(c,0x10000)=0;LZWORD(c,0x14028)=0;}
static int dspInputByte(Context *c,DspRead read,void *opaque,DWORD *result){BYTE value;
    if(LZWORD(c,0x10004)==0){if(!read(c,opaque,dspPeWord(c,0x42ab1c),0xbd5fa0u,0x4000u,0xbd5f84u))return 0;(*c->dspInputPointer)=0xbd5fa0u;}
    LZWORD(c,0x10004)--;if(!dspByte(c,(*c->dspInputPointer),&value))return 0;(*c->dspInputPointer)++;*result=(LZWORD(c,0x10004)&0xffffff00u)|value;return 1;
}
/*403E70 uses signed packed/overflow tests and a local write cursor inside
 *each match. Its first raw write precedes the16-bit mask, including aliases. */
static int dspLzBlock(Context *c,DspRead read,void *opaque,DWORD *result){DWORD flags,one,two,three,four,control,offset,length,write,source;BYTE value;
    while((int32_t)LZWORD(c,0x14020)>0){
        flags=(DWORD)((int32_t)LZWORD(c,0x10000)>>1);LZWORD(c,0x10000)=flags;
        if((flags&0x100u)==0){if(!dspInputByte(c,read,opaque,&one))return 0;flags=0xff00u|(one&255u);LZWORD(c,0x10000)=flags;LZWORD(c,0x14020)--;}
        if(flags&1u){
            if(!dspInputByte(c,read,opaque,&one))return 0;write=LZWORD(c,0x1402c);if(!dspByteStore(c,0xbc5f80u+write,(BYTE)one))return 0;LZWORD(c,0x1402c)=(write+1u)&0xffffu;LZWORD(c,0x14024)++;LZWORD(c,0x14020)--;
        }else{
            if(LZWORD(c,0x14020)==0)break;if(!dspInputByte(c,read,opaque,&one))return 0;control=one&255u;LZWORD(c,0x14020)--;
            if(control>=0xc0u){if(!dspInputByte(c,read,opaque,&one))return 0;offset=(one&255u)|((control&3u)<<8);LZWORD(c,0x14020)--;length=((control>>2)&15u)+4u;}
            else if(control&0x80u){offset=control&31u;if(offset==0){if(!dspInputByte(c,read,opaque,&one))return 0;offset=one&255u;LZWORD(c,0x14020)--;}length=((control>>5)&3u)+2u;}
            else if(control==0x7fu){if(!dspInputByte(c,read,opaque,&one)||!dspInputByte(c,read,opaque,&two)||!dspInputByte(c,read,opaque,&three)||!dspInputByte(c,read,opaque,&four))return 0;length=((one&255u)|((two&255u)<<8))+2u;offset=(three&255u)|((four&255u)<<8);LZWORD(c,0x14020)-=4u;}
            else{if(!dspInputByte(c,read,opaque,&one)||!dspInputByte(c,read,opaque,&two))return 0;offset=(one&255u)|((two&255u)<<8);LZWORD(c,0x14020)-=2u;length=control+4u;}
            write=LZWORD(c,0x1402c);source=(write-offset)&0xffffu;LZWORD(c,0x14024)+=length;
            do{if(!dspByte(c,0xbc5f80u+source,&value)||!dspByteStore(c,0xbc5f80u+write,value))return 0;write=(write+1u)&0xffffu;source=(source+1u)&0xffffu;}while(--length);LZWORD(c,0x1402c)=write;
        }
        if((int32_t)LZWORD(c,0x14024)>8192)break;
    }
    LZWORD(c,0x14024)-=8192u;*result=LZWORD(c,0x14024)+LZWORD(c,0x14020);return 1;
}
static int dspSample(Context *c,DWORD address,WORD *value,int write){DWORD length=2;volatile BYTE *pointer=c->physicalBound?physicalPointer(c,address,2,write?2:1,NULL):dspPointer(c,address,&length);BYTE low,high;if(!pointer)return 0;if(length>=2){if(write)*(volatile WORD *)pointer=*value;else *value=*(volatile WORD *)pointer;return 1;}if(write){*pointer=(BYTE)*value;pointer=c->physicalBound?physicalPointer(c,address+1,1,2,&length):dspPointer(c,address+1,&length);if(!pointer)return 0;*pointer=(BYTE)(*value>>8);}else{low=*pointer;if(!dspByte(c,address+1,&high))return 0;*value=(WORD)(low|high<<8);}return 1;}
/*Original404060 ->403480 ->404140 over the actual shared global arrays.
 *The memory reads/stores use the same fixed VA owners and live PE tables. */
static int dspStreamTransform(Context *c,DWORD output,DWORD extra){
    DWORD source=0xbc5f80u+*(volatile DWORD *)(c->dspLz+0x14028),n,index;BYTE one,two;WORD sample;uint32_t coefficient,value,mixed;int32_t scaled;
    volatile uint32_t *real=(volatile uint32_t *)(c->dspGlobals+0x30000),*imaginary=(volatile uint32_t *)(c->dspGlobals+0x34004);volatile WORD *table=(volatile WORD *)(c->dspGlobals+0x10000);
    for(n=0;n<4096;n++){if(!dspByte(c,source+n,&one))return 0;c->dspGlobals[1+n*2]=one;}
    for(n=0;n<2048;n++){if(!dspByte(c,source+4096+n+2048,&one)||!dspByte(c,source+4096+n,&two))return 0;c->dspGlobals[n*4]=(BYTE)((two&0xf0u)|(one>>4));c->dspGlobals[n*4+2]=(BYTE)((one&15u)|(two<<4));}
    for(n=0;n<2048;n++){coefficient=dspPeWord(c,0x42ab20u+(n*16u/4096u)*4u);index=*(volatile WORD *)(c->dspGlobals+n*4);real[n]=(uint32_t)(int32_t)(short)table[index]*coefficient;index=*(volatile WORD *)(c->dspGlobals+n*4+2);imaginary[n]=(uint32_t)(int32_t)(short)table[index]*coefficient;}
    for(n=2048;n<4096;n++)imaginary[n]=0;for(n=2048;n<4096;n++)real[n]=0;
    *(volatile DWORD *)(c->dspLz+0x14028)=(*(volatile DWORD *)(c->dspLz+0x14028)+8192u)&0xffffu;
    dspFft(c,12,real,imaginary);
    for(n=0;n<4096;n++){
        value=real[n]*((extra&16u)?48u:16u);value+=(int32_t)value<0?0x3fffu:0u;
        scaled=(int32_t)value>>14;if(extra&16u)scaled=(int32_t)((uint32_t)scaled-(uint32_t)((int32_t)value>>31))>>1;
        if(n<32){if(!dspSample(c,0xbc1da4u+output+n*2u,&sample,0))return 0;mixed=(uint32_t)(int32_t)(short)sample*(32u-n)+(uint32_t)scaled*n;mixed+=(int32_t)mixed<0?31u:0u;scaled=(int32_t)mixed>>5;}
        if(scaled>32767)scaled=32767;else if(scaled< -32768)scaled=-32768;sample=(WORD)scaled;if(!dspSample(c,0xbc1da4u+output+n*2u,&sample,1))return 0;
    }
    return 1;
}
typedef int (*DspClose)(Context *,void *,DWORD);
/*41CF00 compressed branch. The two8,128-byte blocks intentionally overlap
 *64 bytes; previous holds untrimmed produced bytes, even on a short tail. */
static int dspCompressedPacket(Context *c,DspRead read,DspClose close,void *opaque,DWORD *count){DWORD n,previous=(DWORD)(*c->stream.channels),produced=0,result,extra;BYTE bytes[4];
    if((*c->stream.remaining)==0){memset(c->stream.scratch,0,0x4000u);*count=0;if(!close(c,opaque,dspPeWord(c,0x42ab1c)))return -1;dspPeStore(c,0x42ab1c,0xffffffffu);return 0;}
    if(previous!=0)for(n=0;n<64;n+=4){if(!dspByte(c,0xbc1da4u+previous+n,&bytes[0])||!dspByte(c,0xbc1da4u+previous+n+1,&bytes[1])||!dspByte(c,0xbc1da4u+previous+n+2,&bytes[2])||!dspByte(c,0xbc1da4u+previous+n+3,&bytes[3]))return -1;memcpy(c->stream.scratch+n,bytes,4);}
    extra=*c->dspStreamingExtra;dspSelectCoefficients(c,extra);
    do{if(!dspLzBlock(c,read,opaque,&result)||!dspStreamTransform(c,produced,*c->dspStreamingExtra))return -1;produced+=8128u;if(result==0)break;}while(produced<8192u);
    (*c->stream.channels)=(int)produced;dspSelectCoefficients(c,(*c->dspStaticExtra));if((*c->stream.remaining)<produced)produced=(*c->stream.remaining);(*c->stream.remaining)-=produced;*count=produced;return 1;
}
#endif
