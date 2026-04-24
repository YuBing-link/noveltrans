package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.StripeCustomer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StripeCustomerMapper extends BaseMapper<StripeCustomer> {
}
