import { createBrowserRouter } from "react-router"

import { RedirectIfAuthed, RequireAuth } from "@/features/auth/guards"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"
import { DashboardPage } from "@/features/dashboard/DashboardPage"
import { LandingPage } from "@/features/landing/LandingPage"
import { ProfilePage } from "@/features/profile/ProfilePage"
import { AppShell } from "@/features/shell/AppShell"
import { NotFoundPage } from "@/features/shell/NotFoundPage"
import { LazyReportPage } from "@/features/reports/LazyReportPage"
import { RouteError } from "@/features/shell/RouteError"
import { JoinPage } from "@/features/splits/JoinPage"
import { SplitPage } from "@/features/splits/SplitPage"

export const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    children: [
      {
        element: <RedirectIfAuthed />,
        children: [
          { path: "/", element: <LandingPage /> },
          { path: "/login", element: <LoginPage /> },
          { path: "/register", element: <RegisterPage /> },
        ],
      },
      { path: "/join/:code", element: <JoinPage /> },
      {
        path: "/app",
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <DashboardPage /> },
              { path: "splits/:splitId", element: <SplitPage /> },
              { path: "splits/:splitId/reports/:reportId", element: <LazyReportPage /> },
              { path: "profile", element: <ProfilePage /> },
              { path: "*", element: <NotFoundPage /> },
            ],
          },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
])
