package com.example.healthconnectwebhook.presentation.viewmodel;

import com.example.healthconnectwebhook.data.preferences.UserPreferences;
import com.example.healthconnectwebhook.data.repository.HealthConnectRepository;
import com.example.healthconnectwebhook.data.repository.WebhookRepository;
import com.example.healthconnectwebhook.domain.usecase.ScheduleHealthSyncUseCase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<UserPreferences> userPreferencesProvider;

  private final Provider<HealthConnectRepository> healthConnectRepositoryProvider;

  private final Provider<WebhookRepository> webhookRepositoryProvider;

  private final Provider<ScheduleHealthSyncUseCase> scheduleHealthSyncUseCaseProvider;

  public MainViewModel_Factory(Provider<UserPreferences> userPreferencesProvider,
      Provider<HealthConnectRepository> healthConnectRepositoryProvider,
      Provider<WebhookRepository> webhookRepositoryProvider,
      Provider<ScheduleHealthSyncUseCase> scheduleHealthSyncUseCaseProvider) {
    this.userPreferencesProvider = userPreferencesProvider;
    this.healthConnectRepositoryProvider = healthConnectRepositoryProvider;
    this.webhookRepositoryProvider = webhookRepositoryProvider;
    this.scheduleHealthSyncUseCaseProvider = scheduleHealthSyncUseCaseProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(userPreferencesProvider.get(), healthConnectRepositoryProvider.get(), webhookRepositoryProvider.get(), scheduleHealthSyncUseCaseProvider.get());
  }

  public static MainViewModel_Factory create(Provider<UserPreferences> userPreferencesProvider,
      Provider<HealthConnectRepository> healthConnectRepositoryProvider,
      Provider<WebhookRepository> webhookRepositoryProvider,
      Provider<ScheduleHealthSyncUseCase> scheduleHealthSyncUseCaseProvider) {
    return new MainViewModel_Factory(userPreferencesProvider, healthConnectRepositoryProvider, webhookRepositoryProvider, scheduleHealthSyncUseCaseProvider);
  }

  public static MainViewModel newInstance(UserPreferences userPreferences,
      HealthConnectRepository healthConnectRepository, WebhookRepository webhookRepository,
      ScheduleHealthSyncUseCase scheduleHealthSyncUseCase) {
    return new MainViewModel(userPreferences, healthConnectRepository, webhookRepository, scheduleHealthSyncUseCase);
  }
}
