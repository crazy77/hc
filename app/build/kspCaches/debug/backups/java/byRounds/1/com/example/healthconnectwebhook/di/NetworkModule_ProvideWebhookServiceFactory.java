package com.example.healthconnectwebhook.di;

import com.example.healthconnectwebhook.data.network.WebhookService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

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
public final class NetworkModule_ProvideWebhookServiceFactory implements Factory<WebhookService> {
  private final Provider<Retrofit> retrofitProvider;

  public NetworkModule_ProvideWebhookServiceFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public WebhookService get() {
    return provideWebhookService(retrofitProvider.get());
  }

  public static NetworkModule_ProvideWebhookServiceFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new NetworkModule_ProvideWebhookServiceFactory(retrofitProvider);
  }

  public static WebhookService provideWebhookService(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideWebhookService(retrofit));
  }
}
