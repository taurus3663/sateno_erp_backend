package com.sateno_b.www.model.listeners;

import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.WpProductTranslationEntity;
import com.sateno_b.www.service.WpProductService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class WpProductEntityListener {

    private final ObjectProvider<WpProductService> wpProductServiceProvider;

    public WpProductEntityListener(ObjectProvider<WpProductService> wpProductServiceProvider) {
        this.wpProductServiceProvider = wpProductServiceProvider;
    }

    @PostUpdate
    public void postUpdate(WpProductEntity wpProductEntity) {
//        System.out.println("postUpdate");
//        WpProductEntity old = wpProductEntity.getSnapshot();
//        if (old == null || old.getTranslations() == null) return;

//        wpProductServiceProvider.ifAvailable(service -> {
//            System.out.println("TEST111");
//            service.postUpdate(old, wpProductEntity);
//        });

    }
}
