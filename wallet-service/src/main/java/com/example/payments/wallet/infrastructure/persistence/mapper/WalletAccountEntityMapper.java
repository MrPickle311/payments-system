package com.example.payments.wallet.infrastructure.persistence.mapper;

import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.infrastructure.persistence.WalletAccountEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WalletAccountEntityMapper {
  WalletAccount toDomain(WalletAccountEntity entity);

  WalletAccountEntity toEntity(WalletAccount domain);
}
