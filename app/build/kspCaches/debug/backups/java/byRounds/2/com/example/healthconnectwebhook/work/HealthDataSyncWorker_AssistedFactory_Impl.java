package com.example.healthconnectwebhook.work;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class HealthDataSyncWorker_AssistedFactory_Impl implements HealthDataSyncWorker_AssistedFactory {
  private final HealthDataSyncWorker_Factory delegateFactory;

  HealthDataSyncWorker_AssistedFactory_Impl(HealthDataSyncWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public HealthDataSyncWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<HealthDataSyncWorker_AssistedFactory> create(
      HealthDataSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new HealthDataSyncWorker_AssistedFactory_Impl(delegateFactory));
  }
}
