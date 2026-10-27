package com.extrarawstyle.ae2us.maintainer;

/**
 * ME库存维持器的单条规则：目标、阈值、固定批量、启用开关与当前订单关联线索。
 * 只保存可序列化的数据；运行期的 Future、watcher 不放在这里。
 */
public final class MaintainerRule {
    public static final int MODE_ITEM = 0;
    public static final int MODE_FLUID = 1;

    /** 目标身份：物品为 IAEItemStack，流体为 IAEFluidStack；数量固定为 1，不参与身份判断。 */
    public Object target;
    public int mode = MODE_ITEM;
    public long threshold;
    public long batch;
    public boolean enabled;

    /** 当前关联订单的 craftID；null 表示没有关联。 */
    public String craftId;
    /** 订单所在 CPU 核心方块坐标，仅作恢复线索。 */
    public boolean hasCpuPos;
    public int cpuDim;
    public long cpuPos;
    public long orderBatch;

    /** 界面展示用：最近一次观测到的库存与状态，不写入存档。 */
    public long observed;
    public boolean observedValid;
    public int status = MaintainerStatus.EMPTY;

    public boolean configured() {
        return this.target != null;
    }

    /** 已配置、已启用、数值合法，才可能参与补货。 */
    public boolean usable() {
        return this.target != null && this.enabled && this.threshold > 0 && this.batch > 0;
    }

    /** 库存严格低于阈值才需要补货；等于阈值不触发。 */
    public boolean belowThreshold(final long stored) {
        return stored < this.threshold;
    }

    public void clearOrder() {
        this.craftId = null;
        this.hasCpuPos = false;
        this.cpuDim = 0;
        this.cpuPos = 0L;
        this.orderBatch = 0L;
    }
}
