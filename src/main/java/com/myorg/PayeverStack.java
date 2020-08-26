package com.myorg;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.eks.*;
import software.amazon.awscdk.services.iam.AccountRootPrincipal;
import software.amazon.awscdk.services.iam.Role;

import java.util.Map;

public class PayeverStack extends Stack {
    public PayeverStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public PayeverStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here
        Vpc vpc = new Vpc(this, "vpc", VpcProps.builder()
                .build());
        Role clusterAdmin = Role.Builder.create(this, "admin")
                .assumedBy(new AccountRootPrincipal())
                .build();
        Cluster cluster = Cluster.Builder.create(this, "fun")
                .vpc(vpc)
                .outputConfigCommand(true)
                .mastersRole(clusterAdmin)
                .version(KubernetesVersion.V1_17)
                .defaultCapacityInstance(new InstanceType("t2.large"))
                .defaultCapacity(2)
                .build();
        cluster.addChart("mysql", HelmChartOptions.builder()
                .chart("mysql")
                .namespace("db")
                .repository("https://kubernetes-charts.storage.googleapis.com/")
                .values(Map.of("configurationFiles",
                        Map.of("mysql_custom.cnf", "[mysqld]\r\nslow_query_log = 1")))
                .build());
        cluster.addChart("prometheus", HelmChartOptions.builder()
                .chart("prometheus")
                .namespace("grafana")
                .repository("https://kubernetes-charts.storage.googleapis.com/")
                .build());
        cluster.addChart("loki", HelmChartOptions.builder()
                .chart("loki-stack")
                .repository("https://grafana.github.io/loki/charts")
                .namespace("grafana")
                .values(Map.of(
                        "fluent-bit", Map.of("enabled", true),
                        "promtail", Map.of("enabled", false)
                ))
                .build());
        cluster.addChart("haproxy", HelmChartOptions.builder()
                .chart("kubernetes-ingress")
                .values(Map.of(
                        "controller",Map.of(
                                "kind","DaemonSet",
                                "ingressClass","haproxy"
                        ))
                )
                .repository("https://haproxytech.github.io/helm-charts")
                .build());
        cluster.addChart("grafana", HelmChartOptions.builder()
                .chart("grafana")
                .namespace("grafana")
                .repository("https://kubernetes-charts.storage.googleapis.com/")
                .values(Map.of(
                        "sidecar", Map.of("datasources", Map.of("enabled", true)),
                        "dashboards", Map.of("default", Map.of(
                                "k8s", Map.of(
                                        "gnetId", 6417,
                                        "datasource", "Prometheus"))),
                        "ingress", Map.of(
                                "enabled", true,
                                "annotations", Map.of(
                                        "kubernetes.io/ingress.class", "haproxy",
                                        "haproxy.ingress.kubernetes.io/rewrite-target", "/"
                                ),
                                "path", "/"
                        )
                ))
                .build());
    }
}
