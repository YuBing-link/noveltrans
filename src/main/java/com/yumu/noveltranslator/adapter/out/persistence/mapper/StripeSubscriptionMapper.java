package com.yumu.noveltranslator.adapter.out.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeSubscription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StripeSubscriptionMapper extends BaseMapper<StripeSubscription> {
}
