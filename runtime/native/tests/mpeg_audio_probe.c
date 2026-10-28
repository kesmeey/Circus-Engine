/* Local PCM oracle adapter; compile as C11. No original game data is included. */
#include <stdio.h>
#include <stdint.h>
#include <math.h>
#ifdef _WIN32
#include <io.h>
#include <fcntl.h>
#endif
#define PL_MPEG_IMPLEMENTATION
#include "../third_party/pl_mpeg.h"
int main(int argc,char **argv) {
    if(argc!=3){fprintf(stderr,"usage: mpeg_audio_probe movie.mpg seconds\n");return 2;}
    double seconds=atof(argv[2]);if(seconds<=0||seconds>600)return 2;
    plm_t *d=plm_create_with_filename(argv[1]);if(!d)return 3;
    plm_set_video_enabled(d,0);
    int rate=plm_get_samplerate(d);if(!rate||!plm_has_headers(d)){plm_destroy(d);return 4;}
#ifdef _WIN32
    _setmode(_fileno(stdout),_O_BINARY);
#endif
    int blocks=0;plm_samples_t *s;
    while((s=plm_decode_audio(d))!=NULL&&s->time<seconds){
        int16_t pcm[2304];
        for(int i=0;i<2304;i++){float v=s->interleaved[i]*32767;pcm[i]=(int16_t)fmaxf(-32768,fminf(32767,v));}
        if(fwrite(pcm,sizeof(int16_t),2304,stdout)!=2304){plm_destroy(d);return 5;}blocks++;
    }
    fprintf(stderr,"{\"rate\":%d,\"channels\":%d,\"blocks\":%d}\n",rate,d->audio_decoder->mode==PLM_AUDIO_MODE_MONO?1:2,blocks);
    plm_destroy(d);return blocks?0:6;
}
