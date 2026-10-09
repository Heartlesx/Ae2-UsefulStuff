package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.client.PatternUploadClient;
import com.extrarawstyle.ae2us.client.UploadSearchHolder;

import appeng.client.gui.implementations.GuiMEMonitorable;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class GuiMEMonitorableUploadMixin implements UploadSearchHolder {

    // PigThings checks focused fields on the current GUI during its client tick.
    @Unique
    private GuiTextField ae2us$uploadSearch;

    @Override
    public void ae2us$setUploadSearch(final GuiTextField search) {
        this.ae2us$uploadSearch = search;
    }

    @Inject(method = "func_73869_a", at = @At("HEAD"), cancellable = true)
    private void ae2us$handleUploadSearchKey(final char character, final int key, final CallbackInfo ci) {
        if (PatternUploadClient.handleKey((GuiScreen) (Object) this, character, key)) {
            ((GuiScreenAccessor) this).ae2us$setKeyHandled(true);
            ci.cancel();
        }
    }
}
