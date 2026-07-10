package com.keepbooking.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.keepbooking.user.dto.UserProfileDto;
import com.keepbooking.user.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "cityId", source = "city.id")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "countryId", source = "country.id")
    @Mapping(target = "countryName", source = "country.name")
    UserProfileDto toDto(User user);
}
