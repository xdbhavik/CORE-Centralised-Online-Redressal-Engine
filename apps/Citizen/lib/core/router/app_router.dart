import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../theme/app_animations.dart';

import '../../features/splash/splash_screen.dart';
import '../../features/onboarding/welcome_screen.dart';
import '../../features/onboarding/onboarding_screen.dart';
import '../../features/onboarding/language_selection_screen.dart';

import '../../features/auth/login_screen.dart';
import '../../features/auth/create_account_screen.dart';
import '../../features/auth/forgot_password_screen.dart';
import '../../features/auth/otp_login_screen.dart';
import '../../features/auth/verify_otp_screen.dart';
import '../../features/home/home_screen.dart';
import '../../features/complaints/create_complaint_screen.dart';
import '../../features/complaints/text_complaint_screen.dart';
import '../../features/complaints/image_complaint_screen.dart';
import '../../features/complaints/ai_assistant_screen.dart';
import '../../features/complaints/ai_review_screen.dart';
import '../../features/complaints/voice_complaint_screen.dart';
import '../../features/complaints/gallery_upload_screen.dart';
import '../../features/complaints/my_complaints_screen.dart';
import '../../features/complaints/complaint_detail_screen.dart';
import '../../features/profile/profile_screen.dart';
import '../../core/widgets/citizen_bottom_navbar.dart';
import '../../features/alerts/alerts_screen.dart';
import '../../features/settings/settings_screen.dart';

/// Helper to create a unified slide + fade transition for all screens
CustomTransitionPage<void> _buildTransitionPage(Widget child, GoRouterState state) {
  return CustomTransitionPage<void>(
    key: state.pageKey,
    child: child,
    transitionDuration: AppAnimations.pageTransition,
    reverseTransitionDuration: AppAnimations.pageTransition,
    transitionsBuilder: (context, animation, secondaryAnimation, child) {
      final curve = CurvedAnimation(
        parent: animation,
        curve: AppAnimations.pageCurve,
      );
      
      // Slide in from right slightly, and fade in
      return SlideTransition(
        position: Tween<Offset>(
          begin: const Offset(0.05, 0.0),
          end: Offset.zero,
        ).animate(curve),
        child: FadeTransition(
          opacity: curve,
          child: child,
        ),
      );
    },
  );
}

/// App routing configuration using go_router.
final GoRouter appRouter = GoRouter(
  initialLocation: '/splash',
  routes: [
    GoRoute(
      path: '/login',
      pageBuilder: (context, state) => _buildTransitionPage(const LoginScreen(), state),
    ),
    GoRoute(
      path: '/register',
      pageBuilder: (context, state) => _buildTransitionPage(const CreateAccountScreen(), state),
    ),
    GoRoute(
      path: '/forgot-password',
      pageBuilder: (context, state) => _buildTransitionPage(const ForgotPasswordScreen(), state),
    ),
    GoRoute(
      path: '/otp-login',
      pageBuilder: (context, state) => _buildTransitionPage(const OtpLoginScreen(), state),
    ),
    GoRoute(
      path: '/verify-otp',
      pageBuilder: (context, state) {
        final extra = state.extra as Map<String, dynamic>?;
        return _buildTransitionPage(
          VerifyOtpScreen(
            contact: extra?['mobile'] ?? '+1 ••• ••• 4492',
            registrationData: extra,
          ), 
          state
        );
      },
    ),
    GoRoute(
      path: '/splash',
      pageBuilder: (context, state) => _buildTransitionPage(const SplashScreen(), state),
    ),
    GoRoute(
      path: '/welcome',
      pageBuilder: (context, state) => _buildTransitionPage(const WelcomeScreen(), state),
    ),
    GoRoute(
      path: '/onboarding',
      pageBuilder: (context, state) => _buildTransitionPage(const OnboardingScreen(), state),
    ),
    GoRoute(
      path: '/language',
      pageBuilder: (context, state) => _buildTransitionPage(const LanguageSelectionScreen(), state),
    ),
    ShellRoute(
      builder: (context, state, child) {
        return CitizenBottomNavbar(child: child);
      },
      routes: [
        GoRoute(
          path: '/home',
          pageBuilder: (context, state) => _buildTransitionPage(const HomeScreen(), state),
        ),
        GoRoute(
          path: '/my-complaints',
          pageBuilder: (context, state) => _buildTransitionPage(
            MyComplaintsScreen(
              initialQuery: state.uri.queryParameters['q'],
              initialStatus: state.uri.queryParameters['status'],
            ),
            state,
          ),
        ),
        GoRoute(
          path: '/alerts',
          pageBuilder: (context, state) => _buildTransitionPage(const AlertsScreen(), state),
        ),
        GoRoute(
          path: '/settings',
          pageBuilder: (context, state) => _buildTransitionPage(const SettingsScreen(), state),
        ),
      ],
    ),
    GoRoute(
      path: '/create-complaint',
      pageBuilder: (context, state) => _buildTransitionPage(const CreateComplaintScreen(), state),
    ),
    GoRoute(
      path: '/text-complaint',
      pageBuilder: (context, state) => _buildTransitionPage(const TextComplaintScreen(), state),
    ),
    GoRoute(
      path: '/image-complaint',
      pageBuilder: (context, state) => _buildTransitionPage(const ImageComplaintScreen(), state),
    ),
    GoRoute(
      path: '/ai-assistant',
      pageBuilder: (context, state) => _buildTransitionPage(const AiAssistantScreen(), state),
    ),
    GoRoute(
      path: '/ai-review',
      pageBuilder: (context, state) => _buildTransitionPage(
        AiReviewScreen(draft: state.extra as dynamic),
        state,
      ),
    ),
    GoRoute(
      path: '/voice-complaint',
      pageBuilder: (context, state) => _buildTransitionPage(const VoiceComplaintScreen(), state),
    ),
    GoRoute(
      path: '/gallery-upload',
      pageBuilder: (context, state) => _buildTransitionPage(const GalleryUploadScreen(), state),
    ),
    GoRoute(
      path: '/complaint-detail/:id',
      pageBuilder: (context, state) {
        final id = int.parse(state.pathParameters['id']!);
        return _buildTransitionPage(ComplaintDetailScreen(complaintId: id), state);
      },
    ),
    GoRoute(
      path: '/profile',
      pageBuilder: (context, state) => _buildTransitionPage(const ProfileScreen(), state),
    ),
  ],
);
