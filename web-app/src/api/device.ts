import { api } from './client';
import type { DeviceTokenResponse } from './types';

export const deviceApi = {
  registerDevice: (deviceId: string) =>
    api.post<string>('/user/register-device', { deviceId }),
  getToken: (deviceId: string) =>
    api.get<DeviceTokenResponse>(`/user/get-token/${deviceId}`),
};
