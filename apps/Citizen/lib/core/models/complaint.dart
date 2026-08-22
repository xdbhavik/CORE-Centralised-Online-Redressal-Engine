/// Data models for the CORE grievance system — Citizen-facing.
/// Maps directly to the backend DTOs documented in BACKEND_REPORT.md.
library;

// ─── Complaint Response (list view) ──────────────────────────────────────────

class ComplaintResponse {
  final int complaintId;
  final String complaintNumber;
  final String status;
  final String? message;

  // List-view enrichment — populated by GET /complaints/my.
  final String? title;
  final String? category;
  final String? createdAt;

  // Duplicate detection outcome — present when duplicate check ran.
  final String? duplicateDecision;
  final double? similarity;
  final List<String> reasons;
  final String? existingComplaintNumber;
  final String? existingStatus;
  final String? scope;
  final bool? resourceMatch;
  final bool? locationMatch;

  /// True when the backend rejected the submission as a confirmed duplicate.
  bool get isDuplicate => status == 'DUPLICATE';

  ComplaintResponse({
    required this.complaintId,
    required this.complaintNumber,
    required this.status,
    this.message,
    this.title,
    this.category,
    this.createdAt,
    this.duplicateDecision,
    this.similarity,
    this.reasons = const [],
    this.existingComplaintNumber,
    this.existingStatus,
    this.scope,
    this.resourceMatch,
    this.locationMatch,
  });

  factory ComplaintResponse.fromJson(Map<String, dynamic> json) {
    return ComplaintResponse(
      complaintId: (json['complaintId'] as num?)?.toInt() ?? 0,
      complaintNumber: json['complaintNumber'] as String? ?? '',
      status: json['status'] as String? ?? '',
      message: json['message'] as String?,
      title: json['title'] as String?,
      category: json['category'] as String?,
      createdAt: json['createdAt'] as String?,
      duplicateDecision: json['duplicateDecision'] as String?,
      similarity: (json['similarity'] as num?)?.toDouble(),
      reasons: (json['reasons'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      existingComplaintNumber: json['existingComplaintNumber'] as String?,
      existingStatus: json['existingStatus'] as String?,
      scope: json['scope'] as String?,
      resourceMatch: json['resourceMatch'] as bool?,
      locationMatch: json['locationMatch'] as bool?,
    );
  }
}

// ─── Complaint Details Response (detail view) ────────────────────────────────

class ComplaintDetailsResponse {
  final String complaintNumber;
  final String? title;
  final String description;
  final String? department;
  final String? category;
  final String? priority;
  final String status;
  final String? officer;
  final String? createdAt;
  final String? updatedAt;
  final String? address;
  final double? latitude;
  final double? longitude;
  final List<String> mediaUrls;

  ComplaintDetailsResponse({
    required this.complaintNumber,
    this.title,
    required this.description,
    this.department,
    this.category,
    this.priority,
    required this.status,
    this.officer,
    this.createdAt,
    this.updatedAt,
    this.address,
    this.latitude,
    this.longitude,
    this.mediaUrls = const [],
  });

  factory ComplaintDetailsResponse.fromJson(Map<String, dynamic> json) {
    return ComplaintDetailsResponse(
      complaintNumber: json['complaintNumber'] as String? ?? '',
      title: json['title'] as String?,
      description: json['description'] as String? ?? '',
      department: json['department'] as String?,
      category: json['category'] as String?,
      priority: json['priority'] as String?,
      status: json['status'] as String? ?? 'REGISTERED',
      officer: json['officer'] as String?,
      createdAt: json['createdAt'] as String?,
      updatedAt: json['updatedAt'] as String?,
      address: json['locationAddress'] as String? ?? json['address'] as String?,
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      mediaUrls: (json['mediaUrls'] as List<dynamic>?)
              ?.map((e) => e as String)
              .toList() ??
          [],
    );
  }
}

// ─── Timeline DTO ────────────────────────────────────────────────────────────

class TimelineDTO {
  final String status;
  final String? time;
  final String? remarks;

  TimelineDTO({
    required this.status,
    this.time,
    this.remarks,
  });

  factory TimelineDTO.fromJson(Map<String, dynamic> json) {
    return TimelineDTO(
      status: json['status'] as String,
      time: json['time'] as String?,
      remarks: json['remarks'] as String?,
    );
  }
}

// ─── AI Response ─────────────────────────────────────────────────────────────

class AIResponse {
  final String? title;
  final String? summary;
  final String? department;
  final String? category;
  final String? priority;
  final int? confidence;
  final String? scope;
  final String? resourceType;
  final String? resourceIdentifier;

  AIResponse({
    this.title,
    this.summary,
    this.department,
    this.category,
    this.priority,
    this.confidence,
    this.scope,
    this.resourceType,
    this.resourceIdentifier,
  });

  factory AIResponse.fromJson(Map<String, dynamic> json) {
    return AIResponse(
      title: json['title'] as String?,
      summary: json['summary'] as String?,
      department: json['department'] as String?,
      category: json['category'] as String?,
      priority: json['priority'] as String?,
      confidence: (json['confidence'] as num?)?.toInt(),
      scope: json['scope'] as String?,
      resourceType: json['resourceType'] as String?,
      resourceIdentifier: json['resourceIdentifier'] as String?,
    );
  }
}

// ─── Duplicate Check Response ────────────────────────────────────────────────

class DuplicateCheckResponse {
  final bool duplicate;
  final int? existingComplaintId;
  final double? similarity;
  final List<String> reasons;
  final String? decision;
  final String? existingComplaintNumber;
  final String? existingStatus;
  final String? scope;
  final bool resourceMatch;
  final bool locationMatch;

  /// Convenience: first reason as a single display string (may be null).
  String? get reason => reasons.isEmpty ? null : reasons.first;

  DuplicateCheckResponse({
    required this.duplicate,
    this.existingComplaintId,
    this.similarity,
    this.reasons = const [],
    this.decision,
    this.existingComplaintNumber,
    this.existingStatus,
    this.scope,
    this.resourceMatch = false,
    this.locationMatch = false,
  });

  factory DuplicateCheckResponse.fromJson(Map<String, dynamic> json) {
    return DuplicateCheckResponse(
      duplicate: json['duplicate'] as bool? ?? false,
      existingComplaintId: (json['existingComplaintId'] as num?)?.toInt(),
      similarity: (json['similarity'] as num?)?.toDouble(),
      reasons: (json['reasons'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      decision: json['decision'] as String?,
      existingComplaintNumber: json['existingComplaintNumber'] as String?,
      existingStatus: json['existingStatus'] as String?,
      scope: json['scope'] as String?,
      resourceMatch: json['resourceMatch'] as bool? ?? false,
      locationMatch: json['locationMatch'] as bool? ?? false,
    );
  }
}

// ─── Chat Response ───────────────────────────────────────────────────────────

class ChatResponse {
  final String answer;

  ChatResponse({required this.answer});

  factory ChatResponse.fromJson(Map<String, dynamic> json) {
    return ChatResponse(
      answer: json['answer'] as String? ?? '',
    );
  }
}

// ─── Translation Response ────────────────────────────────────────────────────

class TranslationResponse {
  final String sourceLanguage;
  final String targetLanguage;
  final String translatedText;

  TranslationResponse({
    required this.sourceLanguage,
    required this.targetLanguage,
    required this.translatedText,
  });

  factory TranslationResponse.fromJson(Map<String, dynamic> json) {
    return TranslationResponse(
      sourceLanguage: json['sourceLanguage'] as String? ?? '',
      targetLanguage: json['targetLanguage'] as String? ?? '',
      translatedText: json['translatedText'] as String? ?? '',
    );
  }
}

// ─── Profile Response ────────────────────────────────────────────────────────

class ProfileResponse {
  final int id;
  final String name;
  final String mobile;
  final String? email;
  final String role;
  final String language;

  ProfileResponse({
    required this.id,
    required this.name,
    required this.mobile,
    this.email,
    required this.role,
    required this.language,
  });

  factory ProfileResponse.fromJson(Map<String, dynamic> json) {
    return ProfileResponse(
      id: json['id'] as int,
      name: json['name'] as String? ?? '',
      mobile: json['mobile'] as String? ?? '',
      email: json['email'] as String?,
      role: json['role'] as String? ?? 'CITIZEN',
      language: json['language'] as String? ?? 'ENGLISH',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'mobile': mobile,
      'email': email,
      'role': role,
      'language': language,
    };
  }
}

// ─── Helper: Data payload for passing between complaint screens ──────────────

class ComplaintDraft {
  final String description;
  final String? title;
  final double? latitude;
  final double? longitude;
  final String? address;
  final String? language;
  final String? imagePath;
  final List<String> mediaPaths;
  final AIResponse? aiAnalysis;
  final DuplicateCheckResponse? duplicateCheck;

  ComplaintDraft({
    required this.description,
    this.title,
    this.latitude,
    this.longitude,
    this.address,
    this.language,
    this.imagePath,
    this.mediaPaths = const [],
    this.aiAnalysis,
    this.duplicateCheck,
  });

  /// All media files attached to this draft (legacy [imagePath] + [mediaPaths]).
  List<String> get allMediaPaths {
    final paths = <String>[...mediaPaths];
    if (imagePath != null && !paths.contains(imagePath)) {
      paths.add(imagePath!);
    }
    return paths;
  }
}
