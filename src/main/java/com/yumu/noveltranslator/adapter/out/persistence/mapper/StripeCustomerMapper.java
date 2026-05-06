package com.yumu.noveltranslator.adapter.out.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeCustomer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StripeCustomerMapper extends BaseMapper<StripeCustomer> {
}
