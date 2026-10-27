package org.circus.graphics;
public final class AlphaComposite implements Composite {
    public static final AlphaComposite SrcOver=new AlphaComposite(1);
    final float alpha;
    private AlphaComposite(float alpha){if(!Float.isFinite(alpha)||alpha<0||alpha>1)throw new IllegalArgumentException("Alpha outside 0..1");this.alpha=alpha;}
    public AlphaComposite derive(float alpha){return new AlphaComposite(alpha);}
}
