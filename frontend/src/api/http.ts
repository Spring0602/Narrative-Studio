import axios from "axios";
import { previewAdapter } from "./previewAdapter";

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error?: { code: string; message: string };
  timestamp?: string;
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 10000,
});

http.interceptors.request.use((config) => {
  if (
    import.meta.env.DEV &&
    new URLSearchParams(location.search).get("preview") === "1"
  ) {
    config.adapter = previewAdapter;
  }
  const token = localStorage.getItem("narrative_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("narrative_token");
      localStorage.removeItem("narrative_user");
      if (location.pathname !== "/login") location.href = "/login";
    }
    return Promise.reject(error);
  },
);
