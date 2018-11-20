package com.gemalto.chaos.experiment;

import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.admin.AdminManager;
import com.gemalto.chaos.constants.ExperimentConstants;
import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.experiment.annotations.NetworkExperiment;
import com.gemalto.chaos.experiment.annotations.ResourceExperiment;
import com.gemalto.chaos.experiment.annotations.StateExperiment;
import com.gemalto.chaos.experiment.enums.ExperimentState;
import com.gemalto.chaos.experiment.impl.GenericContainerExperiment;
import com.gemalto.chaos.notification.ChaosEvent;
import com.gemalto.chaos.notification.NotificationManager;
import com.gemalto.chaos.notification.enums.NotificationLevel;
import com.gemalto.chaos.platform.Platform;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.gemalto.chaos.admin.enums.AdminState.*;
import static com.gemalto.chaos.constants.ExperimentConstants.DEFAULT_TIME_BEFORE_FINALIZATION_SECONDS;
import static com.gemalto.chaos.constants.ExperimentConstants.EXPERIMENT_METHOD_NOT_SET_YET;
import static com.gemalto.chaos.experiment.enums.ExperimentType.*;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ExperimentTest {
    private final Duration stateDuration = Duration.ofMinutes(new Random().nextInt(5) + 5);
    private final Duration networkDuration = Duration.ofMinutes(new Random().nextInt(5) + 5);
    private final Duration resourceDuration = Duration.ofMinutes(new Random().nextInt(5) + 5);
    private Experiment stateExperiment;
    private Experiment networkExperiment;
    private Experiment resourceExperiment;
    @Mock
    private StateContainer stateContainer;
    @Mock
    private NetworkContainer networkContainer;
    @Mock
    private ResourceContainer resourceContainer;
    @MockBean
    private NotificationManager notificationManager;
    @MockBean
    private AdminManager adminManager;
    @Autowired
    private AutowireCapableBeanFactory autowireCapableBeanFactory;

    @Before
    public void setUp () {
        doReturn(STARTED).when(adminManager).getAdminState();

        stateExperiment = Mockito.spy(GenericContainerExperiment.builder()
                                                                .withExperimentType(STATE)
                                                                .withContainer(stateContainer)
                                                                .withDuration(stateDuration)
                                                                .build());
        networkExperiment = Mockito.spy(GenericContainerExperiment.builder()
                                                                  .withExperimentType(NETWORK)
                                                                  .withContainer(networkContainer)
                                                                  .withDuration(networkDuration)
                                                                  .build());
        resourceExperiment = Mockito.spy(GenericContainerExperiment.builder()
                                                                   .withExperimentType(RESOURCE)
                                                                   .withContainer(resourceContainer)
                                                                   .withDuration(resourceDuration)
                                                                   .withFinalzationDuration(Duration.ZERO)
                                                                   .build());
        autowireCapableBeanFactory.autowireBean(stateExperiment);
        autowireCapableBeanFactory.autowireBean(networkExperiment);
        autowireCapableBeanFactory.autowireBean(resourceExperiment);
        doReturn(true).when(adminManager).canRunExperiments();
    }

    @Test
    public void experimentClassTest () {
        final Platform platform = mock(Platform.class);
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        doReturn(true).when(stateContainer).supportsExperimentType(STATE);
        doReturn(platform).when(stateContainer).getPlatform();
        assertNull(stateExperiment.getExperimentLayer());
        assertTrue(stateExperiment.startExperiment());
        assertEquals(platform, stateExperiment.getExperimentLayer());
    }

    @Test
    public void cannotStartWhilePaused () {
        doReturn(PAUSED).when(adminManager).getAdminState();
        assertFalse(stateExperiment.startExperiment());
        assertFalse(networkExperiment.startExperiment());
        assertFalse(resourceExperiment.startExperiment());
        doReturn(DRAIN).when(adminManager).getAdminState();
        assertFalse(stateExperiment.startExperiment());
        assertFalse(networkExperiment.startExperiment());
        assertFalse(resourceExperiment.startExperiment());
    }

    @Test
    public void containerNotStarted () {
        doReturn(ContainerHealth.RUNNING_EXPERIMENT).when(stateContainer).getContainerHealth(STATE);
        doReturn(ContainerHealth.DOES_NOT_EXIST).when(networkContainer).getContainerHealth(NETWORK);
        doReturn(ContainerHealth.RUNNING_EXPERIMENT).when(resourceContainer).getContainerHealth(RESOURCE);
        assertFalse(stateExperiment.startExperiment());
        assertFalse(networkExperiment.startExperiment());
        assertFalse(resourceExperiment.startExperiment());
    }

    @Test(expected = ChaosException.class)
    public void containerWithNoTestMethods () {
        Container container = mock(Container.class);
        Experiment experiment = Mockito.spy(GenericContainerExperiment.builder()
                                                                      .withExperimentType(STATE)
                                                                      .withContainer(container)
                                                                      .build());
        autowireCapableBeanFactory.autowireBean(experiment);

        doReturn(ContainerHealth.NORMAL).when(container).getContainerHealth(STATE);
        doReturn(true).when(container).supportsExperimentType(STATE);
        doReturn(ContainerHealth.NORMAL).when(container).getContainerHealth(STATE);
        experiment.startExperiment();
    }

    @Test
    public void startExperimentWithoutSupportedMethod () {
        Container container = Mockito.mock(Container.class);
        Experiment experiment = Mockito.spy(GenericContainerExperiment.builder()
                                                                      .withExperimentType(STATE)
                                                                      .withContainer(container)
                                                                      .build());
        autowireCapableBeanFactory.autowireBean(experiment);
        doReturn(true).when(adminManager).canRunExperiments();
        doReturn(ContainerHealth.NORMAL).when(container).getContainerHealth(STATE);
        doReturn(false).when(container).supportsExperimentType(STATE);
        assertFalse(experiment.startExperiment());
    }

    @Test
    public void startExperimentFailedToStart(){
        Experiment experiment = Mockito.spy(GenericContainerExperiment.builder()
                                                                      .withExperimentType(STATE)
                                                                      .withContainer(stateContainer)
                                                                      .build());
        doThrow(ChaosException.class).when(stateContainer).startExperiment(experiment);
        autowireCapableBeanFactory.autowireBean(experiment);
        doReturn(true).when(adminManager).canRunExperiments();
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        doReturn(true).when(stateContainer).supportsExperimentType(STATE);
        assertFalse(experiment.startExperiment());
        verify(notificationManager,times(1)).sendNotification(ChaosEvent.builder()
                                                                        .fromExperiment(experiment)
                                                                        .withNotificationLevel(NotificationLevel.WARN)
                                                                        .withMessage(ExperimentConstants.STARTING_NEW_EXPERIMENT)
                                                                        .build());
        verify(notificationManager,times(1)).sendNotification(ChaosEvent.builder()
                                                                        .fromExperiment(experiment)
                                                                        .withNotificationLevel(NotificationLevel.ERROR)
                                                                        .withMessage(ExperimentConstants.FAILED_TO_START_EXPERIMENT)
                                                                        .build());
    }

    @Test
    public void experimentState () {
        // No specific health check method, back to normal, not finalizable (STARTED)
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        doReturn(Boolean.FALSE).when(stateExperiment).isBelowMinimumDuration();
        when(stateExperiment.isFinalizable()).thenReturn(false);
        assertEquals(ExperimentState.STARTED, stateExperiment.getExperimentState());
        verify(stateExperiment, times(0)).doSelfHealing();
        Mockito.reset(stateContainer);
        // No specific health check method, back to normal, is finalizable (FINISHED)
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        when(stateExperiment.isFinalizable()).thenReturn(true);
        assertEquals(ExperimentState.FINISHED, stateExperiment.getExperimentState());
        verify(stateExperiment, times(0)).doSelfHealing();
        Mockito.reset(stateContainer);
        // No specific health check method, container does not exist (FAILED)
        doReturn(ContainerHealth.DOES_NOT_EXIST).when(stateContainer).getContainerHealth(STATE);
        assertEquals(ExperimentState.FAILED, stateExperiment.getExperimentState());
        verify(stateExperiment, times(0)).doSelfHealing();
        Mockito.reset(stateContainer);
        // No specific health check method, container under experiment (STARTED, self healing called)
        doReturn(ContainerHealth.RUNNING_EXPERIMENT).when(stateContainer).getContainerHealth(STATE);
        assertEquals(ExperimentState.STARTED, stateExperiment.getExperimentState());
        verify(stateExperiment, times(1)).doSelfHealing();
        Mockito.reset(stateContainer);
        // No specific health check method, container under experiment (STARTED, self healing max retires reached)
        Mockito.reset(stateExperiment);
        doReturn(ContainerHealth.RUNNING_EXPERIMENT).when(stateContainer).getContainerHealth(STATE);
        stateExperiment.setSelfHealingCounter(new AtomicInteger(ExperimentConstants.DEFAULT_MAXIMUM_SELF_HEALING_RETRIES+1));
        doReturn(true).when(stateExperiment).isOverDuration();
        doReturn(true).when(stateExperiment).canRunSelfHealing();
        assertEquals(ExperimentState.FAILED, stateExperiment.getExperimentState());
        verify(stateExperiment, times(1)).doSelfHealing();
        Mockito.reset(stateContainer);
    }

    @Test
    public void experimentStateWithCallable () {
        // Specific Health Check method, back to normal, not finalizable;
        stateExperiment.setCheckContainerHealth(() -> ContainerHealth.NORMAL);
        when(stateExperiment.isFinalizable()).thenReturn(false);
        assertEquals(ExperimentState.STARTED, stateExperiment.getExperimentState());
        verify(stateContainer, times(0)).getContainerHealth(any());
        Mockito.reset(stateContainer);
    }

    @Test
    public void experimentStateWithCallableThrowingException () {
        stateExperiment.setCheckContainerHealth(() -> {
            throw new RuntimeException();
        });
        when(stateExperiment.isFinalizable()).thenReturn(false);
        doReturn(Boolean.FALSE).when(stateExperiment).isBelowMinimumDuration();
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        assertEquals(ExperimentState.STARTED, stateExperiment.getExperimentState());
        verify(stateContainer, times(0)).getContainerHealth(STATE);
        doReturn(ContainerHealth.RUNNING_EXPERIMENT).when(stateContainer).getContainerHealth(STATE);
        Mockito.reset(stateContainer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void experimentStateWithFinalizableCallable () throws Exception {
        Callable<Void> callable = mock(Callable.class);
        doReturn(Boolean.FALSE).when(stateExperiment).isBelowMinimumDuration();
        stateExperiment.setFinalizeMethod(callable);
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        when(stateExperiment.isFinalizable()).thenReturn(true);
        assertEquals(ExperimentState.FINISHED, stateExperiment.getExperimentState());
        verify(callable, times(1)).call();
        Mockito.reset(stateContainer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void experimentStateWithFinalizableCallableThrowingException () throws Exception {
        Callable<Void> callable = mock(Callable.class);
        doReturn(Boolean.FALSE).when(stateExperiment).isBelowMinimumDuration();
        stateExperiment.setFinalizeMethod(callable);
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        when(stateExperiment.isFinalizable()).thenReturn(true);
        doThrow(new RuntimeException()).when(callable).call();
        assertEquals(ExperimentState.FAILED, stateExperiment.getExperimentState());
        verify(callable, times(1)).call();
        Mockito.reset(stateContainer);
    }

    @Test
    public void doSelfHealing () {
        // Is not over maximumDuration, should not evaluate canRunSelfHealing()
        doReturn(false).when(stateExperiment).isOverDuration();
        doCallRealMethod().when(stateExperiment).doSelfHealing();
        assertEquals(ExperimentState.STARTED,stateExperiment.doSelfHealing());
        verify(stateExperiment, times(0)).canRunSelfHealing();
        reset(notificationManager);
        reset(stateExperiment);
        // Is over maximumDuration and can run self healing. Verify doSelfHealing is called once.
        doReturn(true).when(stateExperiment).isOverDuration();
        doReturn(true).when(stateExperiment).canRunSelfHealing();
        doNothing().when(stateExperiment).callSelfHealing();
        doCallRealMethod().when(stateExperiment).doSelfHealing();
        assertEquals(ExperimentState.STARTED,stateExperiment.doSelfHealing());
        verify(stateExperiment, times(1)).callSelfHealing();
        reset(stateExperiment);
        reset(notificationManager);
        // Is in self healing backoff period
        doReturn(true).when(stateExperiment).isOverDuration();
        doReturn(false).when(stateExperiment).canRunSelfHealing();
        doReturn(true).when(adminManager).canRunSelfHealing();
        assertEquals(ExperimentState.STARTED,stateExperiment.doSelfHealing());
        verify(stateExperiment, times(0)).callSelfHealing();
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.WARN)
                                                                         .withMessage(ExperimentConstants.CANNOT_RUN_SELF_HEALING_AGAIN_YET)
                                                                         .build());
        reset(stateExperiment);
        reset(notificationManager);
        // System is paused and cannot run self healing
        doReturn(true).when(stateExperiment).isOverDuration();
        doReturn(false).when(stateExperiment).canRunSelfHealing();
        doReturn(false).when(adminManager).canRunSelfHealing();
        assertEquals(ExperimentState.STARTED,stateExperiment.doSelfHealing());
        verify(stateExperiment, times(0)).callSelfHealing();
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.WARN)
                                                                         .withMessage(ExperimentConstants.SYSTEM_IS_PAUSED_AND_UNABLE_TO_RUN_SELF_HEALING)
                                                                         .build());
        reset(stateExperiment);
        reset(notificationManager);
        // Exception thrown while running self healing
        doReturn(true).when(stateExperiment).isOverDuration();
        doThrow(new ChaosException()).when(stateExperiment).canRunSelfHealing();
        assertEquals(ExperimentState.STARTED,stateExperiment.doSelfHealing());
        verify(stateExperiment, times(0)).callSelfHealing();
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.ERROR)
                                                                         .withMessage(ExperimentConstants.AN_EXCEPTION_OCCURRED_WHILE_RUNNING_SELF_HEALING)
                                                                         .build());

        reset(stateExperiment);
        reset(notificationManager);
        stateExperiment.setSelfHealingCounter(new AtomicInteger(0));
        // Exception thrown while invoking self healing - SCT-7040
        doReturn(true).when(stateExperiment).isOverDuration();
        doReturn(true).when(stateExperiment).canRunSelfHealing();
        doReturn(true).when(adminManager).canRunSelfHealing();
        doThrow(new ChaosException()).when(stateExperiment).callSelfHealing();
        assertEquals(ExperimentState.STARTED,stateExperiment.doSelfHealing());
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.ERROR)
                                                                         .withMessage(ExperimentConstants.THE_EXPERIMENT_HAS_GONE_ON_TOO_LONG_INVOKING_SELF_HEALING)
                                                                         .build());
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.ERROR)
                                                                         .withMessage(ExperimentConstants.AN_EXCEPTION_OCCURRED_WHILE_RUNNING_SELF_HEALING)
                                                                         .build());

        reset(stateExperiment);
        reset(notificationManager);
        // Maximum self healing retries reached
        doReturn(true).when(stateExperiment).isOverDuration();
        doReturn(true).when(stateExperiment).canRunSelfHealing();
        stateExperiment.setSelfHealingCounter(new AtomicInteger(ExperimentConstants.DEFAULT_MAXIMUM_SELF_HEALING_RETRIES+1));
        assertEquals(ExperimentState.FAILED,stateExperiment.doSelfHealing());
        verify(stateExperiment, times(0)).callSelfHealing();
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.ERROR)
                                                                         .withMessage(ExperimentConstants.MAXIMUM_SELF_HEALING_RETRIES_REACHED)
                                                                         .build());
    }

    @Test
    public void repeatedSelfHealing () {
        // Repeated self healing
        doReturn(true).when(stateExperiment).isOverDuration();
        doReturn(true).when(stateExperiment).canRunSelfHealing();
        doNothing().when(stateExperiment).callSelfHealing();
        doCallRealMethod().when(stateExperiment).doSelfHealing();
        stateExperiment.doSelfHealing();
        verify(stateExperiment, times(1)).callSelfHealing();
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.ERROR)
                                                                         .withMessage(ExperimentConstants.THE_EXPERIMENT_HAS_GONE_ON_TOO_LONG_INVOKING_SELF_HEALING)
                                                                         .build());
        stateExperiment.doSelfHealing();
        verify(stateExperiment, times(2)).callSelfHealing();
        verify(notificationManager, times(1)).sendNotification(ChaosEvent.builder()
                                                                         .fromExperiment(stateExperiment)
                                                                         .withNotificationLevel(NotificationLevel.WARN)
                                                                         .withMessage(ExperimentConstants.THE_EXPERIMENT_HAS_GONE_ON_TOO_LONG_INVOKING_SELF_HEALING + ExperimentConstants.THIS_IS_SELF_HEALING_ATTEMPT_NUMBER + "2.")
                                                                         .build());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void callSelfHealing () throws Exception {
        Instant start = Instant.now();
        Callable<Void> callable = Mockito.mock(Callable.class);
        stateExperiment.setSelfHealingMethod(callable);
        doReturn(null).when(callable).call();
        stateExperiment.callSelfHealing();
        verify(callable, times(1)).call();
        assertFalse(start.isAfter(stateExperiment.getLastSelfHealingTime()));
    }

    @Test(expected = ChaosException.class)
    @SuppressWarnings("unchecked")
    public void callSelfHealingException () throws Exception {
        Instant start = Instant.now();
        Callable<Void> callable = Mockito.mock(Callable.class);
        stateExperiment.setSelfHealingMethod(callable);
        doThrow(new Exception()).when(callable).call();
        stateExperiment.callSelfHealing();
        verify(callable, times(1)).call();
        assertFalse(start.isAfter(stateExperiment.getLastSelfHealingTime()));
    }

    @Test
    public void canRunSelfHealing () {
        assertNull(stateExperiment.getLastSelfHealingTime());
        doReturn(true, false).when(adminManager).canRunSelfHealing();
        assertTrue(stateExperiment.canRunSelfHealing());
        assertFalse(stateExperiment.canRunSelfHealing());
        stateExperiment.callSelfHealing();
        // Insufficient time passed
        reset(stateContainer, adminManager);
        assertNotNull(stateExperiment.getLastSelfHealingTime());
        doReturn(Duration.ofDays(1)).when(stateContainer).getMinimumSelfHealingInterval();
        doReturn(true, false).when(adminManager).canRunSelfHealing();
        assertFalse(stateExperiment.canRunSelfHealing());
        assertFalse(stateExperiment.canRunSelfHealing());
        // Sufficient time passed
        reset(stateContainer, adminManager);
        assertNotNull(stateExperiment.getLastSelfHealingTime());
        doReturn(Duration.ofDays(-1)).when(stateContainer).getMinimumSelfHealingInterval();
        doReturn(true, false).when(adminManager).canRunSelfHealing();
        assertTrue(stateExperiment.canRunSelfHealing());
        assertFalse(stateExperiment.canRunSelfHealing());
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")

    public void isOverDuration () {
        doReturn(Instant.now().minus(stateDuration).plus(Duration.ofMillis(500))).when(stateExperiment).getStartTime();
        await().atLeast(org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS)
               .atMost(org.awaitility.Duration.ONE_SECOND)
               .until(() -> stateExperiment.isOverDuration());
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void isFinalizable () {
        assertNull(stateExperiment.getFinalizationStartTime());
        assertFalse(stateExperiment.isFinalizable());
        assertNotNull(stateExperiment.getFinalizationStartTime());
        doReturn(Instant.now()
                        .minus(Duration.ofSeconds(DEFAULT_TIME_BEFORE_FINALIZATION_SECONDS))
                        .plus(Duration.ofMillis(500))).when(stateExperiment).getFinalizationStartTime();
        await().atLeast(org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS)
               .atMost(org.awaitility.Duration.ONE_SECOND)
               .until(() -> stateExperiment.isFinalizable());
    }

    @Test
    public void setNotificationManager () {
        assertNotNull(stateExperiment.getNotificationManager());
        stateExperiment.setNotificationManager(null);
        assertNull(stateExperiment.getNotificationManager());
    }

    @Test
    public void startExperimentOutOfHours () {
        doReturn(false).when(adminManager).canRunExperiments();
        assertFalse(stateExperiment.startExperiment());
    }

    @Test
    public void preferredExperiment () {
        // Since we're overriding random chance, this is repeated 100 times. 50/50 chance means if this functionality
        // breaks, there is a 1 in 2^100 chance of this passing.
        final String doAnotherThing = "doAnotherThing";
        IntStream.range(0, 100).parallel().forEach(i -> {
            SecondStateContainer mockContainer = mock(SecondStateContainer.class);
            Experiment experiment = Mockito.spy(GenericContainerExperiment.builder()
                                                                          .withContainer(mockContainer)
                                                                          .withExperimentType(STATE)
                                                                          .build());
            experiment.setPreferredExperiment(doAnotherThing);
            autowireCapableBeanFactory.autowireBean(experiment);
            doReturn(ContainerHealth.NORMAL).when(mockContainer).getContainerHealth(STATE);
            doReturn(true).when(mockContainer).supportsExperimentType(STATE);
            experiment.startExperiment();
            assertEquals(doAnotherThing, experiment.getExperimentMethod().getName());
        });
    }

    @Test
    public void getExperimentMethodName(){
        doReturn(ContainerHealth.NORMAL).when(stateContainer).getContainerHealth(STATE);
        doReturn(true).when(stateContainer).supportsExperimentType(STATE);
        doReturn(Boolean.FALSE).when(stateExperiment).isBelowMinimumDuration();
        doReturn(true).when(adminManager).canRunExperiments();
        when(stateExperiment.isFinalizable()).thenReturn(false);
        assertEquals(ExperimentState.STARTED, stateExperiment.getExperimentState());

        assertEquals(EXPERIMENT_METHOD_NOT_SET_YET,stateExperiment.getExperimentMethodName());
        stateExperiment.startExperiment();
        assertNotEquals(EXPERIMENT_METHOD_NOT_SET_YET,stateExperiment.getExperimentMethodName());
    }

    private abstract class StateContainer extends Container {
        @StateExperiment
        public abstract void doAThing (Experiment experiment);
    }

    private abstract class SecondStateContainer extends StateContainer {
        @StateExperiment
        public abstract void doAnotherThing (Experiment experiment);
    }

    private abstract class NetworkContainer extends Container {
        @NetworkExperiment
        public abstract void doAThing (Experiment experiment);
    }

    private abstract class ResourceContainer extends Container {
        @ResourceExperiment
        public abstract void doAThing (Experiment experiment);
    }
}
