package com.myorg;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.HelmChartOptions;
import software.amazon.awscdk.services.eks.KubernetesVersion;
import software.amazon.awscdk.services.iam.AccountRootPrincipal;
import software.amazon.awscdk.services.iam.Role;

import java.util.Map;

public class PayeverStack extends Stack {
    public PayeverStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public PayeverStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        Vpc vpc = new Vpc(this, "vpc", VpcProps.builder()
                .enableDnsHostnames(true)
                .enableDnsSupport(true)
                .build());
        Role clusterAdmin = Role.Builder.create(this, "admin")
                .assumedBy(new AccountRootPrincipal())
                .build();
        Cluster cluster = Cluster.Builder.create(this, "payever")
                .vpc(vpc)
                .outputConfigCommand(true)
                .mastersRole(clusterAdmin)
                .version(KubernetesVersion.V1_17)
                .defaultCapacityInstance(new InstanceType("t2.large"))
                .defaultCapacity(1)
                .build();
        Map mysqlChart = Map.of(
                "id", "mysql",
                "name", "mysql",
                "space", "grafana",
                "repo", "https://kubernetes-charts.storage.googleapis.com/",
                "value", Map.of(
                        "configurationFiles", Map.of("mysql_custom.cnf", "[mysqld]\r\nslow_query_log = 1"),
                        //https://github.com/helm/charts/tree/master/stable/mysql
                        "metrics", Map.of("enabled", true))
        );
        Map prometheusChart = Map.of(
                "id", "prometheus",
                "name", "prometheus",
                "space", "grafana",
                "repo", "https://kubernetes-charts.storage.googleapis.com/",
                "value", Map.of()
        );
        Map haproxyChart = Map.of(
                "id", "haproxy",
                "name", "kubernetes-ingress",
                "space", "ingress",
                "repo", "https://haproxytech.github.io/helm-charts",
                "value", Map.of(
                        "controller", Map.of(
                                "kind", "DaemonSet",
                                "daemonset", Map.of("useHostPort", true),
                                "ingressClass", "haproxy",
                                "service", Map.of("type", "LoadBalancer"))
                )
        );
        Map grafanaChart = Map.of(
                "id", "grafana",
                "name", "grafana",
                "space", "grafana",
                "repo", "https://kubernetes-charts.storage.googleapis.com/",
                "value", Map.of(
                        "ingress", Map.of(
                                "enabled", true,
                                "annotations", Map.of(
                                        "kubernetes.io/ingress.class", "haproxy",
                                        "haproxy.ingress.kubernetes.io/rewrite-target", "/"
                                ),
                                "path", "/"
                        )
                )
        );
        createChart(cluster, haproxyChart, true);
        createChart(cluster, mysqlChart, false);
        createChart(cluster, prometheusChart, false);
        createChart(cluster, grafanaChart, false);
    }

    /**
     * @param cluster
     * @param chart
     * @param wait
     */
    void createChart(Cluster cluster, Map chart, boolean wait) {
        cluster.addChart(chart.get("id").toString(), HelmChartOptions.builder()
                .chart(chart.get("name").toString())
                .namespace(chart.get("space").toString())
                .repository(chart.get("repo").toString())
                .values((Map) chart.get("value"))
                .wait(wait)
                .build());
    }

}
