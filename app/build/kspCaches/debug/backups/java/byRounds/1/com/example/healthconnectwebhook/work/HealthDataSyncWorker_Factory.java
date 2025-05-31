package com.example.healthconnectwebhook.work;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.example.healthconnectwebhook.data.preferences.UserPreferences;
import com.example.healthconnectwebhook.data.repository.HealthConnectRepository;
import com.example.healthconnectwebhook.data.repository.WebhookRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
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
public final class HealthDataSyncWorker_Factory {
  private final Provider<HealthConnectRepository> healthConnectRepositoryProvider;

  private final Provider<WebhookRepository> webhookRepositoryProvider;

  private final Provider<UserPreferences> userPreferencesProvider;

  public HealthDataSyncWorker_Factory(
      Provider<HealthConnectRepository> healthConnectRepositoryProvider,
      Provider<WebhookRepository> webhookRepositoryProvider,
      Provider<UserPreferences> userPreferencesProvider) {
    this.healthConnectRepositoryProvider = healthConnectRepositoryProvider;
    this.webhookRepositoryProvider = webhookRepositoryProvider;
    this.userPreferencesProvider = userPreferencesProvider;
  }

  public HealthDataSyncWorker get(Context context, WorkerParameters workerParams) {
    return newInstance(context, workerParams, healthConnectRepositoryProvider.get(), webhookRepositoryProvider.get(), userPreferencesProvider.get());
  }

  public static HealthDataSyncWorker_Factory create(
      Provider<HealthConnectRepository> healthConnectRepositoryProvider,
      Provider<WebhookRepository> webhookRepositoryProvider,
      Provider<UserPreferences> userPreferencesProvider) {
    return new HealthDataSyncWorker_Factory(healthConnectRepositoryProvider, webhookRepositoryProvider, userPreferencesProvider);
  }

  public static HealthDataSyncWorker newInstance(Context context, WorkerParameters workerParams,
      HealthConnectRepository healthConnectRepository, WebhookRepository webhookRepository,
      UserPreferences userPreferences) {
    return new HealthDataSyncWorker(context, workerParams, healthConnectRepository, webhookRepository, userPreferences);
  }
}
