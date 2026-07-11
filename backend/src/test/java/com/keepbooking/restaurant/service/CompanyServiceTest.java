package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.CompanyDto;
import com.keepbooking.restaurant.dto.CreateCompanyRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.CompanyStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;

    private CompanyService companyService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long COMPANY_ID = 10L;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyRepository, userRepository);
    }

    private Company companyOwnedBy(Long ownerId) {
        return Company.builder().id(COMPANY_ID).owner(User.builder().id(ownerId).build())
                .name("Test Co").status(CompanyStatus.PENDING_MODERATION).build();
    }

    @Test
    void createThrowsWhenUserNotFound() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Test Co");

        assertThatThrownBy(() -> companyService.create(OWNER_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void createSavesCompanyOwnedByRequestingUser() {
        User owner = User.builder().id(OWNER_ID).build();
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(COMPANY_ID);
            return c;
        });

        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Test Co");

        CompanyDto dto = companyService.create(OWNER_ID, request);

        assertThat(dto.getName()).isEqualTo("Test Co");
        assertThat(dto.getStatus()).isEqualTo(CompanyStatus.PENDING_MODERATION);
    }

    @Test
    void getByIdThrowsWhenCompanyNotFound() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getById(COMPANY_ID, OWNER_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    void getByIdThrowsAccessDeniedWhenActorIsNotTheOwner() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(companyOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> companyService.getById(COMPANY_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByIdSucceedsForOwner() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(companyOwnedBy(OWNER_ID)));

        CompanyDto dto = companyService.getById(COMPANY_ID, OWNER_ID);

        assertThat(dto.getId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void setBlockedThrowsWhenCompanyNotFound() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.setBlocked(COMPANY_ID, true))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    void setBlockedTrueSetsStatusBlocked() {
        Company company = companyOwnedBy(OWNER_ID);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        companyService.setBlocked(COMPANY_ID, true);

        assertThat(company.getStatus()).isEqualTo(CompanyStatus.BLOCKED);
    }

    @Test
    void setBlockedFalseSetsStatusActive() {
        Company company = companyOwnedBy(OWNER_ID);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        companyService.setBlocked(COMPANY_ID, false);

        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void getAllCompaniesReturnsPagedResults() {
        Company company = companyOwnedBy(OWNER_ID);
        when(companyRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(company)));

        var result = companyService.getAllCompanies(PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(CompanyDto::getId).containsExactly(COMPANY_ID);
    }
}
