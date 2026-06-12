package com.sm.instagram.platform.common.util;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.consent.*;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.contenttype.ContentTypeRepository;
import com.sm.instagram.platform.currency.Currency;
import com.sm.instagram.platform.currency.CurrencyRepository;
import com.sm.instagram.platform.dictionary.DictionaryEntry;
import com.sm.instagram.platform.dictionary.DictionaryEntryRepository;
import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationRepository;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityPhoto;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityPhotoRepository;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityRepository;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.servicetype.ServiceType;
import com.sm.instagram.platform.servicetype.ServiceTypeRepository;
import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.repository.FileUploadRepository;
import com.sm.instagram.platform.support.faq.models.Faq;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import com.sm.instagram.platform.support.faq.repositories.FaqCategoryRepository;
import com.sm.instagram.platform.support.faq.repositories.FaqRepository;
import com.sm.instagram.platform.support.ticket.models.ResponseAttachment;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;
import com.sm.instagram.platform.support.ticket.models.TicketAttachment;
import com.sm.instagram.platform.support.ticket.models.TicketResponse;
import com.sm.instagram.platform.support.ticket.repositories.ResponseAttachmentRepository;
import com.sm.instagram.platform.support.ticket.repositories.SupportTicketRepository;
import com.sm.instagram.platform.support.ticket.repositories.TicketAttachmentRepository;
import com.sm.instagram.platform.support.ticket.repositories.TicketResponseRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RepositoryResolver {
    private final Map<Class<?>, BaseRepository<?, ?>> repositories = new HashMap<>();

    @Autowired
    public RepositoryResolver(
            ServiceTypeRepository serviceTypeRepository,
            PartnershipOpportunityRepository partnershipOpportunityRepository,
            CityRepository cityRepository,
            PlatformRepository platformRepository,
            UserRepository userRepository,
            CurrencyRepository currencyRepository,
            ContentTypeRepository contentTypeRepository,
            AppliedOpportunityRepository appliedOpportunityRepository,
            AddressRepository addressRepository,
            UserSocialConnectionRepository userSocialConnectionRepository,
            UserPreferencesRepository userPreferencesRepository,
            AppliedOpportunityStatusHistoryRepository appliedOpportunityStatusHistoryRepository,
            AppliedOpportunityContentRepository appliedOpportunityContentRepository,
            PartnershipOpportunityPhotoRepository partnershipOpportunityPhotoRepository,
            DictionaryEntryRepository dictionaryEntryRepository,
            ConsentDefinitionRepository consentDefinitionRepository,
            UserConsentRepository userConsentRepository,
            UserCurrentConsentRepository userCurrentConsentRepository,
            ConsentVersionRepository consentVersionRepository,
            FileUploadRepository fileUploadRepository,
            SupportTicketRepository supportTicketRepository,
            TicketResponseRepository ticketResponseRepository,
            TicketAttachmentRepository ticketAttachmentRepository,
            ResponseAttachmentRepository responseAttachmentRepository,
            FaqRepository faqRepository,
            FaqCategoryRepository faqCategoryRepository,
            NotificationRepository notificationRepository) {

        repositories.put(Platform.class, platformRepository);
        repositories.put(City.class, cityRepository);
        repositories.put(ServiceType.class, serviceTypeRepository);
        repositories.put(PartnershipOpportunity.class, partnershipOpportunityRepository);
        repositories.put(User.class, userRepository);
        repositories.put(Currency.class, currencyRepository);
        repositories.put(ContentType.class, contentTypeRepository);
        repositories.put(AppliedOpportunity.class, appliedOpportunityRepository);
        repositories.put(Address.class, addressRepository);
        repositories.put(UserSocialConnection.class, userSocialConnectionRepository);
        repositories.put(UserPreferences.class, userPreferencesRepository);
        repositories.put(AppliedOpportunityStatusHistory.class, appliedOpportunityStatusHistoryRepository);
        repositories.put(AppliedOpportunityContent.class, appliedOpportunityContentRepository);
        repositories.put(PartnershipOpportunityPhoto.class, partnershipOpportunityPhotoRepository);
        repositories.put(DictionaryEntry.class, dictionaryEntryRepository);
        repositories.put(ConsentDefinition.class, consentDefinitionRepository);
        repositories.put(UserConsent.class, userConsentRepository);
        repositories.put(UserCurrentConsent.class, userCurrentConsentRepository);
        repositories.put(ConsentVersion.class, consentVersionRepository);
        repositories.put(FileUpload.class, fileUploadRepository);
        repositories.put(SupportTicket.class, supportTicketRepository);
        repositories.put(TicketResponse.class, ticketResponseRepository);
        repositories.put(TicketAttachment.class, ticketAttachmentRepository);
        repositories.put(ResponseAttachment.class, responseAttachmentRepository);
        repositories.put(Faq.class, faqRepository);
        repositories.put(FaqCategory.class, faqCategoryRepository);
        repositories.put(Notification.class, notificationRepository);
    }

    @SuppressWarnings("unchecked")
    public <T> BaseRepository<T, Long> getRepository(Class<T> entityType) {
        BaseRepository<?, ?> repository = repositories.get(entityType);
        if (repository == null) {
            throw new IllegalArgumentException("No repository found for entity type: " + entityType.getSimpleName());
        }
        return (BaseRepository<T, Long>) repository;
    }
}

