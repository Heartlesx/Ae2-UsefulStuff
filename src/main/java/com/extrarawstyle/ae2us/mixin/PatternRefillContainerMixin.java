package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.FeatureConfig;
import com.extrarawstyle.ae2us.PatternRefill;
import com.extrarawstyle.ae2us.api.IPatternRefillContainer;
import com.extrarawstyle.ae2us.api.IPatternRefillHost;

import appeng.api.AEApi;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.parts.reporting.AbstractPartEncoder;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * 空白样板自动补充：服务端每 tick 检查空白样板槽，不足 64 个就从终端连接的 ME 网络提取补满。
 * 开关存在终端自身（部件 NBT 或无线终端物品 NBT），通过 @GuiSync 同步给客户端画按钮。
 * 补货放在 detectAndSendChanges 的尾部：打开终端、编码消耗、切换开关都会在下一 tick 被带到这里，
 * 不依赖任何具体编码方法，普通/无线/扩展以及 Fluid Craft for AE2 的流体终端一起覆盖。
 */
@Mixin(value = ContainerPatternEncoder.class, remap = false)
public abstract class PatternRefillContainerMixin implements IPatternRefillContainer {

    // AE2 在本容器链上已占用 96-99，Fluid Craft for AE2 的终端占用 105/106，这里用 94。
    @Unique
    @GuiSync(94)
    public boolean ae2us$refillOn = true;

    @Shadow
    protected IGuiItemObject iGuiItemObject;

    @Shadow
    protected SlotRestrictedInput patternSlotIN;

    @Shadow
    public abstract AbstractPartEncoder getPart();

    // getCellInventory/getPowerSource/getActionSource 声明在祖父类 AEBaseContainer 上：
    // Mixin 不能 shadow 继承来的成员（会报 "was not located in the target class"），改用强转访问。

    @Override
    public boolean ae2us$isPatternRefillOn() {
        return this.ae2us$refillOn;
    }

    @Override
    public void ae2us$togglePatternRefill() {
        if (!Platform.isServer()) {
            return;
        }
        final AbstractPartEncoder part = this.getPart();
        if (part != null) {
            final IPatternRefillHost host = (IPatternRefillHost) part;
            host.ae2us$setPatternRefill(!host.ae2us$isPatternRefill());
            if (part.getHost() != null) {
                part.getHost().markForSave();
            }
        } else if (this.iGuiItemObject != null) {
            final ItemStack stack = this.iGuiItemObject.getItemStack();
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            PatternRefill.write(tag, !PatternRefill.read(tag));
        } else {
            return;
        }
        // 点开当前就补一次，不用等下一 tick。
        this.ae2us$refresh();
        if (this.ae2us$refillOn) {
            this.ae2us$refill();
        }
    }

    // detectAndSendChanges 的运行时方法名；两端都会调用，实际动作只在服务端做。
    @Inject(method = "func_75142_b", at = @At("TAIL"))
    private void ae2us$tick(final CallbackInfo ci) {
        if (!Platform.isServer() || !FeatureConfig.patternRefillEnabled()) {
            return;
        }
        this.ae2us$refresh();
        if (this.ae2us$refillOn) {
            this.ae2us$refill();
        }
    }

    @Unique
    private void ae2us$refresh() {
        final AbstractPartEncoder part = this.getPart();
        if (part != null) {
            this.ae2us$refillOn = ((IPatternRefillHost) part).ae2us$isPatternRefill();
        } else if (this.iGuiItemObject != null) {
            this.ae2us$refillOn = PatternRefill.read(this.iGuiItemObject.getItemStack().getTagCompound());
        }
    }

    @Unique
    private void ae2us$refill() {
        if (this.patternSlotIN == null) {
            return;
        }
        final ItemStack existing = this.patternSlotIN.getStack();
        final int current = existing.isEmpty() ? 0 : existing.getCount();
        final int need = PatternRefill.missing(current);
        if (need <= 0) {
            return;
        }
        if (!existing.isEmpty() && !AEApi.instance().definitions().materials().blankPattern().isSameAs(existing)) {
            return;
        }
        final Optional<ItemStack> blank = AEApi.instance().definitions().materials().blankPattern().maybeStack(need);
        if (!blank.isPresent()) {
            return;
        }
        // 网络离线、没电、安全终端权限不足时这里拿不到东西，静默跳过。
        final AEBaseContainer container = (AEBaseContainer) (Object) this;
        final IAEItemStack extracted = Platform.poweredExtraction(container.getPowerSource(),
                container.getCellInventory(), AEItemStack.fromItemStack(blank.get()), container.getActionSource());
        if (extracted == null || extracted.getStackSize() <= 0) {
            return;
        }
        final ItemStack merged;
        if (existing.isEmpty()) {
            merged = extracted.createItemStack();
        } else {
            merged = existing.copy();
            merged.setCount((int) Math.min(current + extracted.getStackSize(), PatternRefill.CAPACITY));
        }
        this.patternSlotIN.putStack(merged);
    }
}
