import { api } from './client';
import type {
  CheckoutSessionRequest,
  CheckoutSessionResponse,
  SubscriptionStatusResponse,
  PortalSessionResponse,
  PaymentVerificationResponse,
} from './types';

export const subscriptionApi = {
  // 创建支付会话
  checkout: (data: CheckoutSessionRequest) =>
    api.post<CheckoutSessionResponse>('/subscription/checkout', data),

  // 验证支付结果
  verify: (sessionId: string) =>
    api.get<PaymentVerificationResponse>(`/subscription/verify?session_id=${sessionId}`),

  // 获取订阅状态
  getStatus: () =>
    api.get<SubscriptionStatusResponse>('/subscription/status'),

  // 取消订阅
  cancel: () =>
    api.post<SubscriptionStatusResponse>('/subscription/cancel'),

  // 跳转账单管理
  portal: () =>
    api.post<PortalSessionResponse>('/subscription/portal'),
};
