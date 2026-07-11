package com.keepbooking.restaurant.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.CompanyDto;
import com.keepbooking.restaurant.dto.CreateCompanyRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.CompanyStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Transactional
    public CompanyDto create(Long userId, CreateCompanyRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        Company company = Company.builder()
                .owner(owner)
                .name(request.getName())
                .description(request.getDescription())
                .website(request.getWebsite())
                .phone(request.getPhone())
                .email(request.getEmail())
                .build();

        return toDto(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyDto> getMyCompanies(Long userId) {
        return companyRepository.findByOwnerId(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CompanyDto getById(Long companyId, Long userId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_FOUND));
        verifyOwner(company, userId);
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

    private void verifyOwner(Company company, Long userId) {
        if (!company.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this company");
        }
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
