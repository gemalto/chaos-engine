package com.gemalto.chaos.platform.impl;

import com.gemalto.chaos.constants.DataDogConstants;
import com.gemalto.chaos.constants.KubernetesConstants;
import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.container.ContainerManager;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.container.impl.KubernetesPodContainer;
import com.gemalto.chaos.exception.ChaosException;
import com.gemalto.chaos.platform.Platform;
import com.gemalto.chaos.platform.ShellBasedExperiment;
import com.gemalto.chaos.platform.enums.ApiStatus;
import com.gemalto.chaos.platform.enums.PlatformHealth;
import com.gemalto.chaos.platform.enums.PlatformLevel;
import com.gemalto.chaos.shellclient.ShellClient;
import com.gemalto.chaos.shellclient.impl.KubernetesShellClient;
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Exec;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreApi;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.gemalto.chaos.constants.DataDogConstants.DATADOG_CONTAINER_KEY;
import static com.gemalto.chaos.exception.enums.KubernetesChaosErrorCode.K8S_API_ERROR;
import static net.logstash.logback.argument.StructuredArguments.v;

@Component
@ConditionalOnProperty("kubernetes")
@ConfigurationProperties("kubernetes")
public class KubernetesPlatform extends Platform implements ShellBasedExperiment<KubernetesPodContainer> {
    @Autowired
    private ContainerManager containerManager;
    @Autowired
    private CoreApi coreApi;
    @Autowired
    private CoreV1Api coreV1Api;
    @Autowired
    private Exec exec;
    @Autowired
    private AppsV1Api appsV1Api;
    private String namespace = "default";

    @Autowired
    KubernetesPlatform (CoreApi coreApi, CoreV1Api coreV1Api, Exec exec, AppsV1Api appsV1Api) {
        this.coreApi = coreApi;
        this.coreV1Api = coreV1Api;
        this.exec = exec;
        this.appsV1Api = appsV1Api;
        log.info("Kubernetes Platform created");
    }

    public String getNamespace () {
        return namespace;
    }

    public void setNamespace (String namespace) {
        this.namespace = namespace;
    }

    public boolean deletePod (KubernetesPodContainer instance) {
        log.debug("Deleting pod {}", v(DATADOG_CONTAINER_KEY, instance));
        try {
            V1DeleteOptions deleteOptions = new V1DeleteOptionsBuilder().withGracePeriodSeconds(0L).build();
            coreV1Api.deleteNamespacedPod(instance.getPodName(), instance.getNamespace(), deleteOptions, "true", null, null, null);
        } catch (JsonSyntaxException e1) {
            log.debug("Normal exception, see https://github.com/kubernetes-client/java/issues/86");
        } catch (ApiException e) {
            log.error("Could not delete pod", e);
            return false;
        }
        return true;
    }

    public ContainerHealth replicaSetRecovered (KubernetesPodContainer kubernetesPodContainer) {
        return isDesiredReplicas(kubernetesPodContainer) && !podExists(kubernetesPodContainer) ? ContainerHealth.NORMAL : ContainerHealth.RUNNING_EXPERIMENT;
    }

    /**
     * @param instance The Kubernetes Pod Container to retrieve Owner information from
     * @return ContainerHealth
     * <p>
     * In this function we retrieve the desired vs. the actual count of replicas during an experiment.
     * Due to the nature of Kubernetes, there can be 7 different controller types backing a pod:
     * ReplicationController, ReplicaSet, StatefulSet, DaemonSet, Deployment, Job and CronJob
     * (see https://kubernetes.io/docs/concepts/workloads/controllers/garbage-collection/#owners-and-dependents)
     */
    public boolean isDesiredReplicas (KubernetesPodContainer instance) {
        //As stated in https://docs.oracle.com/javase/tutorial/java/nutsandbolts/switch.html, Ensure that the expression in any switch statement is not null to prevent a NullPointerException from being thrown.
        if (instance.getOwnerKind() == null) {
            return false;
        }
        try {
            switch (instance.getOwnerKind()) {
                case ReplicationController:
                    V1ReplicationController rc = coreV1Api.readNamespacedReplicationControllerStatus(instance.getOwnerName(), instance
                            .getNamespace(), "true");
                    return (rc.getStatus().getReplicas().equals(rc.getStatus().getReadyReplicas()));
                case ReplicaSet:
                    V1ReplicaSet replicaSet = appsV1Api.readNamespacedReplicaSetStatus(instance.getOwnerName(), instance
                            .getNamespace(), "true");
                    return (replicaSet.getStatus().getReplicas().equals(replicaSet.getStatus().getReadyReplicas()));
                case StatefulSet:
                    V1StatefulSet statefulSet = appsV1Api.readNamespacedStatefulSetStatus(instance.getOwnerName(), instance
                            .getNamespace(), "true");
                    return (statefulSet.getStatus().getReplicas().equals(statefulSet.getStatus().getReadyReplicas()));
                case DaemonSet:
                    V1DaemonSet daemonSet = appsV1Api.readNamespacedDaemonSetStatus(instance.getOwnerName(), instance.getNamespace(), "true");
                    return (daemonSet.getStatus()
                                     .getCurrentNumberScheduled()
                                     .equals(daemonSet.getStatus().getDesiredNumberScheduled()));
                case Deployment:
                    V1Deployment deployment = appsV1Api.readNamespacedDeploymentStatus(instance.getOwnerName(), instance
                            .getNamespace(), "true");
                    return (deployment.getStatus().getReplicas().equals(deployment.getStatus().getReadyReplicas()));
                case Job:
                case CronJob:
                    log.warn("Job containers are not supported");
                    return false;
                default:
                    log.error("Found unsupported owner reference {}", instance.getOwnerKind());
                    return false;
            }
        } catch (ApiException e) {
            log.error("ApiException was thrown while checking desired replica count.", e);
            return false;
        }
    }

    private boolean podExists (KubernetesPodContainer kubernetesPodContainer) {
        try {
            return coreV1Api.readNamespacedPodStatus(kubernetesPodContainer.getPodName(), kubernetesPodContainer.getNamespace(), "true") != null;
        } catch (ApiException e) {
            log.debug("Container {} no more exists", v(DataDogConstants.DATADOG_CONTAINER_KEY, kubernetesPodContainer));
        }
        return false;
    }

    public ContainerHealth checkHealth (KubernetesPodContainer kubernetesPodContainer) {
        try {
            V1Pod result = coreV1Api.readNamespacedPodStatus(kubernetesPodContainer.getPodName(), kubernetesPodContainer
                    .getNamespace(), "true");
            //if there's any not ready container, return DOES_NOT_EXIST
            return Optional.ofNullable(result)
                           .map(V1Pod::getStatus)
                           .map(V1PodStatus::getContainerStatuses)
                           .map(Collection::stream)
                           .map(s -> s.anyMatch(status -> !status.isReady()))
                           .map(aBoolean -> aBoolean ? ContainerHealth.RUNNING_EXPERIMENT : ContainerHealth.NORMAL)
                           .orElse(ContainerHealth.DOES_NOT_EXIST);
        } catch (ApiException e) {
            log.error("Exception when checking container health", e);
            return ContainerHealth.DOES_NOT_EXIST;
        }
    }

    @Override
    public ApiStatus getApiStatus () {
        try {
            coreApi.getAPIVersions().getVersions();
            return ApiStatus.OK;
        } catch (ApiException e) {
            log.error("Kubernetes API health check failed", e);
            return ApiStatus.ERROR;
        }
    }

    @Override
    public PlatformLevel getPlatformLevel () {
        return PlatformLevel.PAAS;
    }

    @Override
    public PlatformHealth getPlatformHealth () {
        try {
            V1PodList pods = listAllPodsInNamespace();
            return (!pods.getItems().isEmpty()) ? PlatformHealth.OK : PlatformHealth.DEGRADED;
        } catch (ApiException e) {
            log.error("Kubernetes Platform health check failed", e);
            return PlatformHealth.FAILED;
        }
    }

    private V1PodList listAllPodsInNamespace () throws ApiException {
        return coreV1Api.listNamespacedPod(namespace, "", "", "", true, "", 0, "", 0, false);
    }

    @Override
    protected List<Container> generateRoster () {
        final List<Container> containerList = new ArrayList<>();
        try {
            V1PodList pods = listAllPodsInNamespace();
            containerList.addAll(pods.getItems().stream().map(this::fromKubernetesAPIPod).collect(Collectors.toSet()));
            return containerList;
        } catch (ApiException e) {
            log.error("Could not generate Kubernetes roster", e);
            return containerList;
        }
    }

    @Override
    public boolean isContainerRecycled (Container container) {
        KubernetesPodContainer kubernetesPodContainer = (KubernetesPodContainer) container;
        return isContainerRestarted(kubernetesPodContainer, ((KubernetesPodContainer) container).getTargetedSubcontainer());
    }

    private boolean isContainerRestarted (KubernetesPodContainer container, String subContainerName) {
        V1Pod v1Pod;
        try {
            v1Pod = coreV1Api.readNamespacedPodStatus(container.getPodName(), container.getNamespace(), "true");
        } catch (ApiException e) {
            if (e.getMessage().equals(KubernetesConstants.KUBERNETES_POD_NOT_FOUND_ERROR_MESSAGE)) {
                return replicaSetRecovered(container) == ContainerHealth.NORMAL;
            }
            throw new ChaosException(K8S_API_ERROR, e);
        }
        return v1Pod.getStatus()
                    .getContainerStatuses()
                    .stream()
                    .filter(v1ContainerStatus -> v1ContainerStatus.getName().equals(subContainerName))
                    .map(v1ContainerStatus -> v1ContainerStatus.getState().getRunning())
                    .filter(Objects::nonNull)
                    .peek(v1ContainerStateRunning -> log.debug("Evaluating last restart time from {}", v("v1ContainerStateRunning", v1ContainerStateRunning)))
                    .anyMatch(v1ContainerStateRunning -> v1ContainerStateRunning.getStartedAt()
                                                                                .isAfter(container.getExperimentStartTime()
                                                                                                  .toEpochMilli()));
    }

    KubernetesPodContainer fromKubernetesAPIPod (V1Pod pod) {
        KubernetesPodContainer container = containerManager.getMatchingContainer(KubernetesPodContainer.class, pod.getMetadata()
                                                                                                                  .getName());
        if (container == null) {
            container = KubernetesPodContainer.builder()
                                              .withPodName(pod.getMetadata().getName())
                                              .withNamespace(pod.getMetadata().getNamespace())
                                              .withLabels(pod.getMetadata().getLabels())
                                              .withKubernetesPlatform(this)
                                              .isBackedByController(CollectionUtils.isNotEmpty(pod.getMetadata()
                                                                                                  .getOwnerReferences()))
                                              .withOwnerKind(Optional.of(pod.getMetadata().getOwnerReferences())
                                                                     .flatMap(list -> list.stream().findFirst())
                                                                     .map(V1OwnerReference::getKind)
                                                                     .orElse(""))
                                              .withOwnerName(Optional.of(pod.getMetadata().getOwnerReferences())
                                                                     .flatMap(list -> list.stream().findFirst())
                                                                     .map(V1OwnerReference::getName)
                                                                     .orElse(""))
                                              .withSubcontainers(pod.getSpec()
                                                                    .getContainers()
                                                                    .stream()
                                                                    .map(V1Container::getName)
                                                                    .collect(Collectors.toList()))
                                              .build();
            log.info("Found new Kubernetes Pod Container {}", v(DATADOG_CONTAINER_KEY, container));
            containerManager.offer(container);
        } else {
            log.debug("Found existing Kubernetes Pod Container {}", v(DATADOG_CONTAINER_KEY, container));
        }
        return container;
    }

    @Override
    public void recycleContainer (KubernetesPodContainer container) {
        deletePod(container);
    }

    @Override
    public ShellClient getConnectedShellClient (KubernetesPodContainer container) {
        log.debug("Creating shell client into {}", v(DATADOG_CONTAINER_KEY, container));
        return KubernetesShellClient.builder()
                                    .withExec(exec)
                                    .withContainerName(container.getTargetedSubcontainer())
                                    .withPodName(container.getPodName())
                                    .withNamespace(container.getNamespace())
                                    .build();
    }
}