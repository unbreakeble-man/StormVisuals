package dev.stormvisuals.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.*;
import dev.stormvisuals.module.CrosshairSettings;
import dev.stormvisuals.module.CustomCrosshair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Shared analytic geometry for the HUD and editor; transparent outlines use one pass. */
public final class CrosshairRenderer {
    private static final Identifier SHADER=Identifier.fromNamespaceAndPath("stormvisuals","core/crosshair");
    private static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("stormvisuals","pipeline/crosshair"))
            .withVertexShader(SHADER).withFragmentShader(SHADER)
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS).build());
    private static long cachedRevision=-1;
    private static final List<Shape> SHAPES=new ArrayList<>(32);
    private record Shape(int kind,float x,float y,float angle,float size,float thickness,float opacity,int color,int outlineColor,float outlineWidth,boolean centerDot) {}
    private CrosshairRenderer() {}

    private static void rebuild(){
        CrosshairSettings s=CustomCrosshair.settings();
        if(cachedRevision==CustomCrosshair.revision())return;
        cachedRevision=CustomCrosshair.revision(); SHAPES.clear();
        switch(s.style){
            case SQUARE -> add(s,3,0,0,0,s.squareSize,1);
            case RING -> add(s,1,0,0,0,s.ringSize,1);
            case V -> add(s,4,0,0,0,s.vSize,1);
            case CROSS -> {for(int i=0;i<4;i++){double a=Math.PI*i/2;float d=s.crossGap+s.crossLength/2;add(s,0,(float)Math.cos(a)*d,(float)Math.sin(a)*d,i*90,s.crossLength,s.crossWidth);}}
            case CUSTOM -> {for(var e:s.elements){
                if(!e.visible)continue;
                int kind=switch(e.kind){case LINE->0;case RING->1;case DOT->2;case SQUARE->e.filled?5:3;case V->4;};
                SHAPES.add(new Shape(kind,e.x,e.y,e.rotation,e.size,e.thickness,
                        s.opacity*e.opacity/100,e.ownSettings?e.color:s.color,e.ownSettings?e.outlineColor:s.outlineColor,
                        (e.ownSettings?e.outline:s.outline)?(e.ownSettings?e.outlineWidth:s.outlineWidth):0,e.kind==CrosshairSettings.Kind.DOT&&e.x==0&&e.y==0));
            }}
        }
    }
    private static void add(CrosshairSettings s,int kind,float x,float y,float angle,float size,float thickness){
        SHAPES.add(new Shape(kind,x,y,angle,size,thickness,s.opacity,s.color,s.outlineColor,s.outline?s.outlineWidth:0,false));
    }
    /** Returns the visible lower bound, relative to the requested center. */
    public static float draw(GuiGraphics g,float cx,float cy,float zoom,boolean hovered,float pulse,float alpha){
        rebuild(); CrosshairSettings s=CustomCrosshair.settings();
        float bottom=0;
        for(Shape shape:SHAPES){
            float k=s.style==CrosshairSettings.Style.V||shape.centerDot?1:1+pulse*.25f;
            float x=shape.x,y=shape.y,size=shape.size;
            if(s.style==CrosshairSettings.Style.CROSS){double a=Math.toRadians(shape.angle);x+=(float)Math.cos(a)*pulse*3;y+=(float)Math.sin(a)*pulse*3;}
            else {x*=k;y*=k;size*=k;}
            float h=shape.kind==0?shape.thickness:size;
            float rad=(float)Math.toRadians(shape.angle), sn=(float)Math.sin(rad), cs=(float)Math.cos(rad);
            float extent=(Math.abs(sn)*size+Math.abs(cs)*h)*.5f+shape.outlineWidth;
            if(shape.kind==4)extent=Math.max(0,Math.abs(sn)*size*.5f-cs*h*.5f)+shape.thickness*.5f+shape.outlineWidth;
            bottom=Math.max(bottom,y+extent);
            int color=hovered?s.hoverColor:shape.color;
            int argb=Math.round(shape.opacity*alpha*2.55f)<<24|color;
            if((argb>>>24)==0)continue;
            g.pose().pushMatrix();g.pose().translate(cx+x*zoom,cy+y*zoom);g.pose().rotate(rad);g.pose().scale(zoom,zoom);
            submit(g,shape.kind,size*.5f,h*.5f,shape.thickness,shape.outlineWidth,argb,shape.outlineColor);
            g.pose().popMatrix();
        }
        return bottom;
    }
    private static void submit(GuiGraphics g,int kind,float hx,float hy,float stroke,float outline,int color,int outlineColor){
        Matrix3x2fc pose=new Matrix3x2f(g.pose());
        var window=Minecraft.getInstance().getWindow();
        float pixels=(float)Math.hypot(pose.m00(),pose.m01())*window.getWidth()/Math.max(1f,window.getGuiScaledWidth());
        float pad=outline+1.5f/Math.max(.01f,pixels);
        float bx=hx+pad,by=hy+pad;
        if(kind==4){bx+=stroke/2;by+=stroke/2;}
        ScreenRectangle bounds=new ScreenRectangle((int)Math.floor(-bx),(int)Math.floor(-by),(int)Math.ceil(bx)*2+2,(int)Math.ceil(by)*2+2).transformMaxBounds(pose);
        ScreenRectangle scissor=g.scissorStack.peek();if(scissor!=null)bounds=scissor.intersection(bounds);if(bounds==null)return;
        g.guiRenderState.submitGuiElement(new State(pose,kind,hx,hy,stroke,outline,color,outlineColor,bx,by,scissor,bounds));
    }
    private record State(Matrix3x2fc pose,int kind,float hx,float hy,float stroke,float outline,int color,int outlineColor,
                         float bx,float by,ScreenRectangle scissorArea,ScreenRectangle bounds) implements GuiElementRenderState {
        public RenderPipeline pipeline(){return PIPELINE;}
        public TextureSetup textureSetup(){return TextureSetup.noTexture();}
        public void buildVertices(VertexConsumer v){vertex(v,-bx,-by);vertex(v,-bx,by);vertex(v,bx,by);vertex(v,bx,-by);}
        private void vertex(VertexConsumer v,float x,float y){
            // Reuse overlay/light/normal attributes as immutable, per-shape analytic parameters.
            v.addVertexWith2DPose(pose,x,y).setColor(color).setUv(x,y)
                    .setUv1(Math.round(hx*128),Math.round(hy*128))
                    .setUv2(kind*2048+Math.round(stroke*128),Math.round(outline*128))
                    .setNormal(((outlineColor>>16&255)/255f)*2-1,((outlineColor>>8&255)/255f)*2-1,((outlineColor&255)/255f)*2-1);
        }
    }
    public static void attackIndicator(GuiGraphics g,Minecraft mc,float bottom){
        if(mc.options.attackIndicator().get()!=AttackIndicatorStatus.CROSSHAIR)return;
        float strength=mc.player.getAttackStrengthScale(0);
        boolean ready=false;
        if(mc.crosshairPickEntity instanceof LivingEntity target && strength>=1){
            ready=mc.player.getCurrentItemAttackStrengthDelay()>5 && target.isAlive();
            AttackRange range=mc.player.getActiveItem().get(DataComponents.ATTACK_RANGE);
            ready &= range==null || (mc.hitResult!=null && range.isInRange(mc.player,mc.hitResult.getLocation()));
        }
        int y=g.guiHeight()/2+(int)Math.ceil(Math.max(9,bottom+3)),x=g.guiWidth()/2-8;
        if(ready)g.blitSprite(RenderPipelines.CROSSHAIR,FULL,x,y,16,16);
        else if(strength<1){
            g.blitSprite(RenderPipelines.CROSSHAIR,BACKGROUND,x,y,16,4);
            g.blitSprite(RenderPipelines.CROSSHAIR,PROGRESS,16,4,0,0,x,y,(int)(strength*17),4);
        }
    }
    private static final Identifier FULL=Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full"),
            BACKGROUND=Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background"),
            PROGRESS=Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
}
