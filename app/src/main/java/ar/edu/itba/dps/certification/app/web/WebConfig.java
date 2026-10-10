package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.config.RequestActor;
import ar.edu.itba.dps.certification.application.catalogue.usecase.BrowseParties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    private final BrowseParties parties;
    private final RequestActor actors;

    WebConfig(BrowseParties parties, RequestActor actors) {
        this.parties = parties;
        this.actors = actors;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ActorInterceptor(parties, actors)).addPathPatterns("/api/**");
    }
}
