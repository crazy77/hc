package com.example.healthconnectwebhook.domain.usecase;

import androidx.work.WorkManager;
import com.example.healthconnectwebhook.data.preferences.UserPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class ScheduleHealthSyncUseCase_Factory implements Factory<ScheduleHealthSyncUseCase> {
  private final Provider<WorkManager> workManagerProvider;

  private final Provider<UserPreferences> userPreferencesProvider;

  public ScheduleHealthSyncUseCase_Factory(Provider<WorkManager> workManagerProvider,
      Provider<UserPreferences> userPreferencesProvider) {
    this.workManagerProvider = workManagerProvider;
    this.userPreferencesProvider = userPreferencesProvider;
  }

  @Override
  public ScheduleHealthSyncUseCase get() {
    return newInstance(workManagerProvider.get(), userPreferencesProvider.get());
  }

  public static ScheduleHealthSyncUseCase_Factory create(Provider<WorkManager> workManagerProvider,
      Provider<UserPreferences> userPreferencesProvider) {
    return new ScheduleHealthSyncUseCase_Factory(workManagerProvider, userPreferencesProvider);
  }

  public static ScheduleHealthSyncUseCase newInstance(WorkManager workManager,
      UserPreferences userPreferences) {
    return new ScheduleHealthSyncUseCase(workManager, userPreferences);
  }
}
