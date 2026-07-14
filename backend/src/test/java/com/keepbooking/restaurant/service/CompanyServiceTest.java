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
import com.keepbooking.common.security.AccessControlService;
import com.keepbooking.restaurant.dto.CompanyDto;
import com.keepbooking.restaurant.dto.CreateCompanyRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.CompanyStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.user.dto.UserProfileDto;
import com.keepbooking.user.mapper.UserMapper;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    private CompanyService companyService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long COMPANY_ID = 10L;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyRepository, userRepository, new AccessControlService(userRepository), userMapper);
    }

    private void stubOwner() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(
                User.builder().id(OWNER_ID).role(UserRole.ROLE_COMPANY_ADMIN).companyId(COMPANY_ID).build()));
    }

    private Company company() {
        return Company.builder().id(COMPANY_ID).name("Test Co").status(CompanyStatus.PENDING_MODERATION).build();
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
    void createThrowsWhenUserAlreadyBelongsToACompany() {
        stubOwner();
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Another Co");

        assertThatThrownBy(() -> companyService.create(OWNER_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void createSavesCompanyAndPromotesRequestingUserToCompanyAdmin() {
        User user = User.builder().id(OWNER_ID).role(UserRole.ROLE_USER).build();
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(COMPANY_ID);
            return c;
        });

        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Test Co");

        CompanyDto dto = companyService.create(OWNER_ID, request);

        assertThat(dto.getName()).isEqualTo("Test Co");
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_COMPANY_ADMIN);
        assertThat(user.getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void createOnBehalfSavesActiveCompanyWithNoAdminYet() {
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(COMPANY_ID);
            return c;
        });

        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Vkusno i Tochka");

        CompanyDto dto = companyService.createOnBehalf(request);

        assertThat(dto.getName()).isEqualTo("Vkusno i Tochka");
        assertThat(dto.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void assignAdminThrowsAccessDeniedWhenActorDoesNotManageTheCompany() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));

        assertThatThrownBy(() -> companyService.assignAdmin(999L, COMPANY_ID, "target@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignAdminPromotesTargetUserToCompanyAdmin() {
        stubOwner();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        User target = User.builder().id(50L).email("target@test.com").role(UserRole.ROLE_USER).build();
        when(userRepository.findByEmailAndDeletedAtIsNull("target@test.com")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenReturn(UserProfileDto.builder().id(50L).build());

        companyService.assignAdmin(OWNER_ID, COMPANY_ID, "target@test.com");

        assertThat(target.getRole()).isEqualTo(UserRole.ROLE_COMPANY_ADMIN);
        assertThat(target.getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void assignAdminThrowsWhenTargetAlreadyBelongsToAnotherCompany() {
        stubOwner();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        User target = User.builder().id(50L).email("target@test.com").role(UserRole.ROLE_COMPANY_ADMIN).companyId(999L).build();
        when(userRepository.findByEmailAndDeletedAtIsNull("target@test.com")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> companyService.assignAdmin(OWNER_ID, COMPANY_ID, "target@test.com"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void getAdminsThrowsAccessDeniedWhenActorDoesNotManageTheCompany() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));

        assertThatThrownBy(() -> companyService.getAdmins(999L, COMPANY_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAdminsReturnsCompanyAdminsForTheOwner() {
        stubOwner();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        User admin = User.builder().id(50L).role(UserRole.ROLE_COMPANY_ADMIN).companyId(COMPANY_ID).build();
        when(userRepository.findByCompanyIdAndRole(COMPANY_ID, UserRole.ROLE_COMPANY_ADMIN)).thenReturn(java.util.List.of(admin));
        when(userMapper.toDto(admin)).thenReturn(UserProfileDto.builder().id(50L).build());

        var result = companyService.getAdmins(OWNER_ID, COMPANY_ID);

        assertThat(result).extracting(UserProfileDto::getId).containsExactly(50L);
    }

    @Test
    void revokeAdminResetsTargetToPlainUser() {
        stubOwner();
        User target = User.builder().id(50L).role(UserRole.ROLE_COMPANY_ADMIN).companyId(COMPANY_ID).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        companyService.revokeAdmin(OWNER_ID, COMPANY_ID, 50L);

        assertThat(target.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(target.getCompanyId()).isNull();
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
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));

        assertThatThrownBy(() -> companyService.getById(COMPANY_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByIdSucceedsForOwner() {
        stubOwner();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));

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
        Company company = company();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        companyService.setBlocked(COMPANY_ID, true);

        assertThat(company.getStatus()).isEqualTo(CompanyStatus.BLOCKED);
    }

    @Test
    void setBlockedFalseSetsStatusActive() {
        Company company = company();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        companyService.setBlocked(COMPANY_ID, false);

        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void getAllCompaniesReturnsPagedResults() {
        Company company = company();
        when(companyRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(company)));

        var result = companyService.getAllCompanies(PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(CompanyDto::getId).containsExactly(COMPANY_ID);
    }
}
