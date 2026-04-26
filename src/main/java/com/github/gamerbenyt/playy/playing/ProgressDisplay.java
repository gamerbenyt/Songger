package com.github.gamerbenyt.playy.playing;

import com.github.gamerbenyt.playy.Playy;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Objects;

public class ProgressDisplay {
    private static ProgressDisplay instance = null;
    public static ProgressDisplay getInstance() {
        if (instance == null) {
            instance = new ProgressDisplay();
        }
        return instance;
    }
    private ProgressDisplay() {}

    public MutableText topText = Text.empty();
    public MutableText bottomText = Text.empty();
    public int fade = 0;

    public void setText(MutableText bottomText, MutableText topText) {
        this.bottomText = bottomText;
        this.topText = topText;
        fade = 100;
    }

    public void onRenderHUD(DrawContext context, int heldItemTooltipFade) {
        if (fade <= 0) {
            return;
        }

        int bottomTextWidth = Playy.MC.textRenderer.getWidth(bottomText);
        int topTextWidth = Playy.MC.textRenderer.getWidth(topText);
        int bottomTextX = (Playy.MC.getWindow().getScaledWidth() - bottomTextWidth) / 2;
        int topTextX = (Playy.MC.getWindow().getScaledWidth() - topTextWidth) / 2;
        int bottomTextY = Playy.MC.getWindow().getScaledHeight() - 59;
        if (!Playy.MC.interactionManager.hasStatusBars()) {
            bottomTextY += 14;
        }
        if (heldItemTooltipFade > 0) {
            bottomTextY -= 12;
        }
        int topTextY = bottomTextY - 12;

        int opacity = (int)((float)this.fade * 256.0F / 10.0F);
        if (opacity > 255) {
            opacity = 255;
        }

        Objects.requireNonNull(Playy.MC.textRenderer);
        context.drawTextWithShadow(Playy.MC.textRenderer, bottomText, bottomTextX, bottomTextY, 16777215 + (opacity << 24));
        context.drawTextWithShadow(Playy.MC.textRenderer, topText, topTextX, topTextY, 16777215 + (opacity << 24));
    }

    public void onTick() {
        if (fade > 0) {
            fade--;
        }
    }
}
