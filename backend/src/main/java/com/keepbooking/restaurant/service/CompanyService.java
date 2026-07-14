package com.keepbooking.restaurant.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.dto.PageResponse;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final UserMapper userMapper;

    /**
     * Self-service onboarding: the creator becomes this company's first COMPANY_ADMIN
     * immediately. A user can only ever be scoped to one company (see V020), so a user who
     * already administers/works at a company can't create a second one.
     */
    @Transactional
    public CompanyDto create(Long userId, CreateCompanyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getCompanyId() != null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "You already belong to a company");
        }

        Company company = Company.builder()
                .name(request.getName())
                .description(request.getDescription())
                .website(request.getWebsite())
                .phone(request.getPhone())
                .email(request.getEmail())
                .build();
        company = companyRepository.save(company);

        user.setRole(UserRole.ROLE_COMPANY_ADMIN);
        user.setCompanyId(company.getId());
        userRepository.save(user);

        return toDto(company);
    }

    /**
     * Support-assisted onboarding: SUPER_ADMIN creates the company on behalf of a client who
     * contacted the platform directly, before any COMPANY_ADMIN is attached - see assignAdmin().
     */
    @Transactional
    public CompanyDto createOnBehalf(CreateCompanyRequest request) {
        Company company = Company.builder()
                .name(request.getName())
                .description(request.getDescription())
                .website(request.getWebsite())
                .phone(request.getPhone())
                .email(request.getEmail())
                .status(CompanyStatus.ACTIVE)
                .build();
        return toDto(companyRepository.save(company));
    }

    /**
     * Attaches an already-registered user (by email) as this company's COMPANY_ADMIN. Callable
     * by SUPER_ADMIN (onboarding) or an existing COMPANY_ADMIN of the same company (adding a
     * co-admin) - accessControlService covers both.
     */
    @Transactional
    public UserProfileDto assignAdmin(Long actorId, Long companyId, String targetEmail) {
        companyRepository.findById(companyId).orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_FOUND));
        accessControlService.verifyCanManageCompany(actorId, companyId);

        User target = userRepository.findByEmailAndDeletedAtIsNull(targetEmail)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (target.getCompanyId() != null && !target.getCompanyId().equals(companyId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "User already belongs to a different company");
        }

        target.setRole(UserRole.ROLE_COMPANY_ADMIN);
        target.setCompanyId(companyId);
        target.setRestaurantId(null);

        return userMapper.toDto(userRepository.save(target));
    }

    @Transactional(readOnly = true)
    public List<UserProfileDto> getAdmins(Long actorId, Long companyId) {
        companyRepository.findById(companyId).orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_FOUND));
        accessControlService.verifyCanManageCompany(actorId, companyId);
        return userRepository.findByCompanyIdAndRole(companyId, UserRole.ROLE_COMPANY_ADMIN).stream()
                .map(userMapper::toDto)
                .toList();
    }

    @Transactional
    public void revokeAdmin(Long actorId, Long companyId, Long targetUserId) {
        accessControlService.verifyCanManageCompany(actorId, companyId);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (target.getRole() != UserRole.ROLE_COMPANY_ADMIN || !companyId.equals(target.getCompanyId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "User is not an admin of this company");
        }

        target.setRole(UserRole.ROLE_USER);
        target.setCompanyId(null);
        userRepository.save(target);
    }

    @Transactional(readOnly = true)
    public List<CompanyDto> getMyCompanies(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getCompanyId() == null) {
            return List.of();
        }
        return companyRepository.findById(user.getCompanyId()).map(this::toDto).map(List::of).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public CompanyDto getById(Long companyId, Long userId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_FOUND));
        accessControlService.verifyCanManageCompany(userId, companyId);
        return toDto(company);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyDto> getAllCompanies(Pageable pageable) {
        Page<Company> page = companyRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional
    public void setBlocked(Long companyId, boolean blocked) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_FOUND));
        company.setStatus(blocked ? CompanyStatus.BLOCKED : CompanyStatus.ACTIVE);
        companyRepository.save(company);
    }

    private CompanyDto toDto(Company company) {
        return CompanyDto.builder()
                .id(company.getId())
                .name(company.getName())
                .description(company.getDescription())
                .logoUrl(company.getLogoUrl())
                .website(company.getWebsite())
                .phone(company.getPhone())
                .email(company.getEmail())
                .status(company.getStatus())
                .build();
    }
}
