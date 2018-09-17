package com.gemalto.chaos.constants;

public abstract class AwsRDSConstants {
    public static final String AWS_RDS_AVAILABLE = "available";
    public static final String AWS_RDS_BACKING_UP = "backing-up";
    public static final String AWS_RDS_FAILING_OVER = "failing-over";
    public static final String AWS_RDS_PROMOTING = "promoting";
    public static final String AWS_RDS_CHAOS_SECURITY_GROUP = "ChaosEngine RDS Security Group";
    public static final String AWS_RDS_CHAOS_SECURITY_GROUP_DESCRIPTION = "(DO NOT USE) Security Group used by Chaos Engine to simulate random network failures.";

    private AwsRDSConstants () {
    }
    /*
    Sources:
    https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Aurora.Status.html
     */
}

