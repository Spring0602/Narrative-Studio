import { http, type ApiEnvelope } from "./http";

export interface AccountProfile {
  id: number;
  username: string;
  displayName: string;
  email?: string | null;
  emailVerifiedAt?: string | null;
  role: string;
  status: "ACTIVE" | "DISABLED";
}
export async function getAccount() {
  return (await http.get<ApiEnvelope<AccountProfile>>("/account")).data.data;
}
export async function requestEmailVerification(
  email: string,
  password: string,
) {
  return (
    await http.post<ApiEnvelope<string>>("/account/email-verifications", {
      email,
      password,
    })
  ).data.data;
}
export async function confirmEmailVerification(token: string) {
  await http.post("/auth/email-verifications/confirm", { token });
}
export async function changePassword(
  currentPassword: string,
  newPassword: string,
) {
  await http.put("/account/password", { currentPassword, newPassword });
}
export async function requestPasswordReset(email: string) {
  return (
    await http.post<ApiEnvelope<string>>("/auth/password-resets", { email })
  ).data.data;
}
export async function confirmPasswordReset(token: string, password: string) {
  await http.post("/auth/password-resets/confirm", { token, password });
}
