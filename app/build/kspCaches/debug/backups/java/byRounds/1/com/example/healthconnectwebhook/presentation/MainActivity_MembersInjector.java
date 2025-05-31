package com.example.healthconnectwebhook.presentation;

import com.example.healthconnectwebhook.data.repository.HealthConnectRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<HealthConnectRepository> healthConnectRepositoryProvider;

  public MainActivity_MembersInjector(
      Provider<HealthConnectRepository> healthConnectRepositoryProvider) {
    this.healthConnectRepositoryProvider = healthConnectRepositoryProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<HealthConnectRepository> healthConnectRepositoryProvider) {
    return new MainActivity_MembersInjector(healthConnectRepositoryProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectHealthConnectRepository(instance, healthConnectRepositoryProvider.get());
  }

  @InjectedFieldSignature("com.example.healthconnectwebhook.presentation.MainActivity.healthConnectRepository")
  public static void injectHealthConnectRepository(MainActivity instance,
      HealthConnectRepository healthConnectRepository) {
    instance.healthConnectRepository = healthConnectRepository;
  }
}
