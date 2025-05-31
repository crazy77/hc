package com.example.healthconnectwebhook.data.repository;

import android.content.Context;
import com.example.healthconnectwebhook.data.network.WebhookService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class WebhookRepository_Factory implements Factory<WebhookRepository> {
  private final Provider<WebhookService> webhookServiceProvider;

  private final Provider<Context> contextProvider;

  public WebhookRepository_Factory(Provider<WebhookService> webhookServiceProvider,
      Provider<Context> contextProvider) {
    this.webhookServiceProvider = webhookServiceProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public WebhookRepository get() {
    return newInstance(webhookServiceProvider.get(), contextProvider.get());
  }

  public static WebhookRepository_Factory create(Provider<WebhookService> webhookServiceProvider,
      Provider<Context> contextProvider) {
    return new WebhookRepository_Factory(webhookServiceProvider, contextProvider);
  }

  public static WebhookRepository newInstance(WebhookService webhookService, Context context) {
    return new WebhookRepository(webhookService, context);
  }
}
