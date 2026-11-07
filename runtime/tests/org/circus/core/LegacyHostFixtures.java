package org.circus.core;

import java.io.*;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

/** Frozen self-authored portable host bytes, not original Windows game saves.
 * Exported from immutable stage1 classes36ffe7e1... with the prior tested3E->3C
 * fixture conversion. New host fields must not redefine this legacy input. */
final class LegacyHostFixtures {
    private LegacyHostFixtures(){}
    private static final String DD3C="eNrt2ktrE1EYxvE5bY03vLTereKtiy6dyUmbA4IXBtyoeN9nJslOURSKblrUiLpQEe9WEF2L4qqiIK50ka+h4KcY/xNOZEqHZlJt1eR92l8zaZ8M6cmcnikZ38/vcSIbp5HIypLpj5y95/x27DNr7AvxZ/zR2Hnja+3IcFSvR9GYz/3xcbbrTndm2oD7hw66zr5KPtgdau2ZYjV0Q1ebUjWo6rBozEg1MJ72RksV7Vb0iDaByeuwpE3BGDcYLRa8oFgoKCf3ffL5t4mb8cZU7cfT6y2eg7Jm5MDX1tspWbRgO0+mbCUPQk8qUpGKVKTy/1d+LScLvILJayMVqUhFKt2yyLSRLLtM6/yp/UhHOtKRjnT+fuefinK6Od3920skEolE0ukr/H4ctSdn8fdc5BKd2OKMZwW9lkrcSiTznd6MZ6/NtyX60JO4n3z7I3l7ys6NtLnT7rFdbvPMOrXXvNqh1eUoWS9XmWPKHUR1kBnHSXtH2N6JWQ65+fk/MfXx/unDzZ/12NndZ2drzq5GS7AUy7AcK7ASq7Aa/RjAGqzFOqzHBgxiC7ZiG7ZjB3ZiF4YwbGf+MRzHCZy0fxHO4CzO4Twu4CIu4TKu4CquoYb48qEbuIXbuIO7uIf7eICHeITHeIJnmMQLvMQrvMYbvMU7TOE9PuAjPuEzvjB0jJdivBTjpRgvxVipjdiEzRiURUMikUgkc8lPWn+vyg==";
    static byte[] dd3cEmpty()throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(DD3C)))){
            byte[] block=new byte[4096];for(int n;(n=in.read(block))!=-1;)bytes.write(block,0,n);
        }
        byte[] result=bytes.toByteArray();
        if(result.length!=11600||!Binary.sha256(result).equals("477be5f1b24e537ac78a46f8ca5e030ee2fcc05a17c75f2527c814bf352c71e0"))throw new IOException("Corrupt legacy DD fixture");
        return result;
    }
}
