package com.devtunde.posbackend.payments.internal.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.devtunde.posbackend.payments.api.PaymentGateway;
import com.devtunde.posbackend.payments.internal.gateway.MonnifyGateway;
import com.devtunde.posbackend.payments.internal.gateway.PaymentGatewayRouter;
import com.devtunde.posbackend.payments.internal.gateway.PaystackGateway;
import com.monnify.Monnify;
import com.monnify.services.refund.RefundService;
import com.monnify.services.transaction.TransactionService;

@Configuration
@Profile("!test")
public class PaymentsConfig {

    @Bean
    @ConditionalOnExpression("'${app.payments.providers.paystack.secret-key:}' != ''")
    PaystackGateway paystackGateway(PaymentsProperties properties) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.providers().paystack().connectTimeout());
        factory.setReadTimeout(properties.providers().paystack().readTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.providers().paystack().baseUrl())
                .defaultHeader(
                        "Authorization",
                        "Bearer " + properties.providers().paystack().secretKey())
                .requestFactory(factory)
                .build();

        return new PaystackGateway(restClient, properties.currencyCode());
    }

    @Bean
    @ConditionalOnExpression("'${app.payments.providers.monnify.api-key:}' !=''"
            + " and '${app.payments.providers.monnify.secret-key:}' !=''")
    MonnifyGateway monnifyGateway(PaymentsProperties properties) {

        Monnify.initialize(
                properties.providers().monnify().apiKey(),
                properties.providers().monnify().secretKey());

        return new MonnifyGateway(
                new TransactionService(),
                new RefundService(),
                properties.currencyCode(),
                properties.providers().monnify().contractCode());
    }

    @Bean
    PaymentGateway paymentGatewayRouter(
            ObjectProvider<PaystackGateway> paystackGateway, ObjectProvider<MonnifyGateway> monnifyGateway) {
        return new PaymentGatewayRouter(paystackGateway.getIfAvailable(), monnifyGateway.getIfAvailable());
    }
}
