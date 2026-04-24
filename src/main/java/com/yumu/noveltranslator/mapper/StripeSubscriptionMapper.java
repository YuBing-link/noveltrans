package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.StripeSubscription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StripeSubscriptionMapper extends BaseMapper<StripeSubscription> {
}
