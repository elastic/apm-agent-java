package co.elastic.apm.agent.awslambda.helper;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class LoadBalancerElbTargetGroupArnMetadata {

    private LoadBalancerElbTargetGroupArnMetadata() {}
    public LoadBalancerElbTargetGroupArnMetadata(String targetGroupArn) {
        this.targetGroupArn = targetGroupArn;
    }
    private String targetGroupArn;
    private String cloudRegion;
    private String accountId;
    private String targetGroupName;

    public LoadBalancerElbTargetGroupArnMetadata withCloudRegion(String cloudRegion) {
        this.cloudRegion = cloudRegion;
        return this;
    }

    @Nullable
    public String getCloudRegion() {
        return cloudRegion;
    }

    public LoadBalancerElbTargetGroupArnMetadata withAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public LoadBalancerElbTargetGroupArnMetadata withTargetGroupName(String targetGroupName) {
        this.targetGroupName = targetGroupName;
        return this;
    }

    @Nonnull
    public String getTargetGroupArn() {
        return targetGroupArn;
    }

    @Nullable
    public String getAccountId() {
        return accountId;
    }

    @Nullable
    public String getTargetGroupName() {
        return targetGroupName;
    }
}
