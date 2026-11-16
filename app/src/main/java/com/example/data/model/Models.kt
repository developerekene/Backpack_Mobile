package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

// ==========================================
// 1. Roles & Permissions
// ==========================================

enum class UserRole {
    STUDENT,
    INSTRUCTOR,
    ORGANISATION,
    SUPER_ADMIN;

    val label: String
        get() = when (this) {
            STUDENT -> "Student"
            INSTRUCTOR -> "Instructor"
            ORGANISATION -> "Organisation Admin"
            SUPER_ADMIN -> "Super Admin"
        }

    val technicalRole: String
        get() = when (this) {
            STUDENT -> "student"
            INSTRUCTOR -> "instructor"
            ORGANISATION -> "organization"
            SUPER_ADMIN -> "super_admin"
        }

    companion object {
        fun fromString(roleStr: String?): UserRole {
            if (roleStr == null) return STUDENT
            val clean = roleStr.lowercase().trim()
            return when {
                clean.contains("super_admin") -> SUPER_ADMIN
                clean.contains("instructor") || clean.contains("teacher") || clean.contains("lecturer") || clean.contains("faculty") -> INSTRUCTOR
                clean.contains("organisation") || clean.contains("organization") || clean.contains("admin") || clean.contains("school") || clean.contains("org") || clean.contains("institution") -> ORGANISATION
                else -> STUDENT
            }
        }
    }
}

// ==========================================
// 2. Paystack & Financial Models (Web Mapped)
// ==========================================

@IgnoreExtraProperties
data class PaystackSubaccount(
    val subaccount_code: String = "",
    val business_name: String = "",
    val bank_code: String = "",
    val bank_name: String? = null,
    val country: String? = "NG",
    val currency: String? = "NGN",
    val account_number: String = "",
    val account_name: String? = null,
    val percentage_charge: Double = 85.0,
    val description: String? = null,
    val is_verified: Boolean? = null,
    val mode: String? = "live",
    val updatedAt: String? = null
)

// ==========================================
// 3. User, Accommodations, Organization & Membership
// ==========================================

@IgnoreExtraProperties
data class UserDocument(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val category: String = "cv", // "cv" | "certificate" | "id_proof" | "transcript" | "other"
    val uploadedAt: String = ""
)

@IgnoreExtraProperties
data class SpecialNeedsAccommodations(
    val enabled: Boolean = false,
    val disabilityCategories: List<String> = emptyList(),
    val otherCategoryDescription: String? = null,
    val examTimeMultiplier: Double = 1.0,
    val preferredFormatting: List<String> = emptyList(),
    val medicalNotes: String? = null,
    val emergencyHealthNotice: String? = null,
    val requestedServices: List<String> = emptyList(),
    val allowInstructorVisibility: Boolean? = true,
    val allowReviewerVisibility: Boolean? = false,
    val updatedAt: String? = null
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val userId: String = "",
    val name: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "student",
    val bio: String? = null,
    val headline: String? = null,
    val cvUrl: String? = null,
    val orgId: String? = null,
    val ownerId: String? = null,
    val logoUrl: String? = null,
    val institutionName: String? = null,
    val description: String? = null,
    val location: String? = null,
    val baseCurrency: String? = null,
    val orgType: String? = null,
    val address: String? = null,
    val registrationId: String? = null,
    val isAccredited: Boolean = false,
    val accreditingBody: String? = null,
    val accreditationStatus: String? = null,
    val accreditationDocUrl: String? = null,
    val motto: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val plan: String? = null,
    val paystackSubaccount: PaystackSubaccount? = null,
    val totalEnrolledStudents: Int = 0,
    val kycVerified: Boolean = false,
    val kycDocumentUrl: String? = null,
    val userDocuments: List<UserDocument> = emptyList(),
    val accommodations: SpecialNeedsAccommodations? = null,
    val isSuspended: Boolean = false,
    val createdAtIso: String? = null,
    val tuitionSettlementStatus: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val userRole: UserRole
        get() = UserRole.fromString(role)
}

@IgnoreExtraProperties
data class Organization(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val logoUrl: String? = null,
    val ownerId: String = "",
    val baseCurrency: String = "USD",
    val location: String? = null,
    val orgType: String? = null,
    val kycVerified: Boolean = false,
    val kycDocumentUrl: String? = null,
    val address: String? = null,
    val registrationId: String? = null,
    val isAccredited: Boolean = false,
    val accreditingBody: String? = null,
    val accreditationStatus: String? = null,
    val accreditationDocUrl: String? = null,
    val motto: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val plan: String? = null,
    val paystackSubaccount: PaystackSubaccount? = null,
    val isDeleted: Boolean = false
)

@IgnoreExtraProperties
data class AdditionalDocument(
    val id: String = "",
    val name: String = "",
    val url: String = ""
)

@IgnoreExtraProperties
data class ReapplicationRecord(
    val id: String = "",
    val sessionId: String? = null,
    val sessionName: String? = null,
    val appliedAt: String = "",
    val status: String = "rejected",
    val rejectedAt: String? = null,
    val rejectionReason: String? = null
)

@IgnoreExtraProperties
data class EnrollmentRequest(
    val id: String = "",
    val userId: String = "",
    val orgId: String = "",
    val userName: String = "",
    val userEmail: String? = null,
    val courseId: String = "",
    val courseTitle: String = "",
    val sessionId: String? = null,
    val sessionName: String? = null,
    val status: String = "pending", // "pending" | "approved" | "rejected" | "cancelled"
    val paymentStatus: String? = null, // "unpaid" | "paid"
    val paymentMethod: String? = null,
    val documents: Map<String, String> = emptyMap(),
    val additionalDocuments: List<AdditionalDocument> = emptyList(),
    val studentNotes: String? = null,
    val rejectionReason: String? = null,
    val rejectedAt: String? = null,
    val rejectedSessionId: String? = null,
    val reapplicationHistory: List<ReapplicationRecord> = emptyList(),
    val isSponsored: Boolean = false,
    val sponsorName: String? = null,
    val sponsorEmail: String? = null,
    val accommodations: SpecialNeedsAccommodations? = null,
    val appliedAt: String = ""
)

@IgnoreExtraProperties
data class OrgJoinRequest(
    val id: String = "",
    val userId: String = "",
    val orgId: String = "",
    val userName: String = "",
    val orgName: String = "",
    val status: String = "pending"
)

@IgnoreExtraProperties
data class CourseDonation(
    val id: String = "",
    val courseId: String = "",
    val courseTitle: String? = null,
    val orgId: String = "",
    val amount: Double = 0.0,
    val currency: String = "USD",
    val donorName: String? = null,
    val donorEmail: String = "",
    val isAnonymous: Boolean = false,
    val donationType: String = "general",
    val numberOfStudents: Int? = null,
    val createdAt: String = ""
)

data class VocationalAdmissionGate(
    val isVocational: Boolean = false,
    val isDonationFunded: Boolean = false,
    val tuitionCostPerStudent: Double = 0.0,
    val totalDonations: Double = 0.0,
    val maxAdmissibleStudents: Int = -1,
    val currentlyAdmittedCount: Int = 0,
    val remainingSpots: Int = Int.MAX_VALUE,
    val canAdmitMore: Boolean = true,
    val currency: String = "USD",
    val nextSeatNeededAmount: Double = 0.0
)

@IgnoreExtraProperties
data class OrgMember(
    val id: String = "",
    val userId: String? = null,
    val orgId: String = "",
    val name: String = "",
    val email: String = "",
    val department: String? = null,
    val role: String = "student", // "student" | "instructor" | "admin"
    val status: String = "invited", // "invited" | "active" | "pending" | "graduated"
    val joinedAt: String = "",
    val courseIds: List<String> = emptyList(),
    val inviteNote: String? = null,
    val requiresPayment: Boolean = false,
    val requiresDocuments: Boolean = false,
    val requiredDocNames: List<String> = emptyList()
)

// ==========================================
// 4. Courses, Modules, Materials & Certificates
// ==========================================

@IgnoreExtraProperties
data class CertificateConfig(
    val enabled: Boolean = false,
    val customText: String = "Certificate of Completion",
    val orgName: String? = null,
    val gradeLevel: String? = null,
    val qualificationTitle: String? = null,
    val signatureUrl: String? = null,
    val authorizedSealUrl: String? = null,
    val themeColor: String = "#0078D4",
    val textColor: String = "#0F172A",
    val layout: String = "classic"
)

@IgnoreExtraProperties
data class CourseModuleItem(
    val id: String = "",
    val title: String = "",
    val type: String = "text", // "text" | "video" | "document" | "audio" | "picture" | "embed" | "link"
    val description: String? = null,
    val content: String? = null,
    val url: String? = null,
    val videoUrl: String? = null,
    val documentUrl: String? = null,
    val audioUrl: String? = null,
    val pictureUrl: String? = null,
    val embedUrl: String? = null,
    val duration: String? = null
)

typealias LessonNote = CourseModuleItem

@IgnoreExtraProperties
data class CourseModule(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val items: List<CourseModuleItem> = emptyList(),
    val media: List<String> = emptyList()
)

@IgnoreExtraProperties
data class CourseMaterial(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val url: String = "",
    val type: String = "link", // "pdf" | "video" | "doc" | "link"
    val content: String? = null,
    val createdAt: String = ""
)

@IgnoreExtraProperties
data class CourseItem(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val createdBy: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val description: String = "",
    val coverImageUrl: String? = null,
    val price: Double = 0.0,
    val currency: String = "USD",
    val qualificationTitle: String? = null,
    val qualificationType: String? = null,
    val instructorName: String? = null,
    val instructorId: String? = null,
    val instructors: List<String> = emptyList(),
    val pacing: String = "self-paced", // "self-paced" | "live-online" | "blended"
    val durationWeeks: Int? = null,
    val totalHours: Int? = null,
    val language: String? = null,
    val requirements: String? = null,
    val applicationProcess: String? = null,
    val instructorRequirements: String? = null,
    val admissionStatus: String = "open", // "open" | "closed"
    val activeSessionId: String? = null,
    val activeSessionName: String? = null,
    val fundingModel: String? = null, // "direct_tuition" | "donations_sponsorships"
    val tuitionCostPerStudent: Double? = null,
    val totalDonationsReceived: Double? = null,
    val paymentTermsAllowed: List<String> = emptyList(),
    val enrolledCount: Int = 0,
    val modules: List<CourseModule> = emptyList(),
    val certificateConfig: CertificateConfig? = null
) {
    fun calculateProgressPercent(progress: UserProgress?): Double {
        val totalTrackables = modules.size + modules.sumOf { it.items.size }
        if (totalTrackables <= 0 || progress == null) return 0.0
        val completedModIds = progress.completedModuleIds.toSet()
        val completedItemIds = progress.completedItemIds.toSet()
        var completed = 0
        modules.forEach { mod ->
            if (mod.id in completedModIds) completed++
            mod.items.forEach { item ->
                if (item.id in completedItemIds) completed++
            }
        }
        return (completed.toDouble() / totalTrackables.toDouble()) * 100.0
    }
}

// ==========================================
// 5. Assessments, Submissions & User Progress
// ==========================================

@IgnoreExtraProperties
data class AssessmentQuestionOption(
    val id: String = "",
    val text: String = "",
    val isCorrect: Boolean = false
)

@IgnoreExtraProperties
data class AssessmentQuestionSummary(
    val id: String = "",
    val prompt: String = "",
    val format: String = "objective", // "objective" | "essay"
    val questionType: String? = null, // "multiple_choice" | "true_false" | "essay"
    val points: Int = 0,
    val options: List<AssessmentQuestionOption> = emptyList(),
    val correctBoolean: Boolean? = null,
    val rubric: String? = null
)

@IgnoreExtraProperties
data class Assessment(
    @DocumentId val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val type: String = "assignment", // "assignment" | "quiz" | "test" | "exam" | "project" | "classwork"
    val subject: String? = null,
    val gradeLevel: String? = null,
    val description: String? = null,
    val attachments: List<String> = emptyList(),
    val instructorName: String? = null,
    val dueDate: String = "",
    val startDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val durationMinutes: Int? = null,
    val maxScore: Int = 100,
    val passingScore: Int? = null,
    val gradingType: String? = null,
    val weight: Double? = null,
    val submissionMethod: String? = "online",
    val allowedFileTypes: List<String> = emptyList(),
    val maxFileSizeMb: Int? = null,
    val allowResubmission: Boolean = false,
    val requireStudentComments: Boolean = false,
    val isGroup: Boolean = false,
    val questions: List<AssessmentQuestionSummary> = emptyList()
) {
    val isMobileSubmittable: Boolean
        get() = type.lowercase().trim() in setOf("assignment", "classwork", "project")
}

@IgnoreExtraProperties
data class Submission(
    @DocumentId val id: String = "",
    val assessmentId: String = "",
    val userId: String = "",
    val userName: String? = null,
    val courseId: String = "",
    val submittedAt: String = "",
    val content: String = "",
    val fileUrl: String? = null,
    val score: Double? = null,
    val feedback: String? = null,
    val status: String = "submitted", // "submitted" | "graded"
    val autoGradedPoints: Double? = null,
    val autoGradedMax: Double? = null,
    val pendingEssayGrading: Boolean = false,
    val essayScores: Map<String, Double> = emptyMap(),
    val answers: Map<String, String> = emptyMap()
)

typealias MobileSubmission = Submission

@IgnoreExtraProperties
data class UserProgress(
    @DocumentId val id: String? = null,
    val userId: String = "",
    val courseId: String = "",
    val completedModuleIds: List<String> = emptyList(),
    val completedItemIds: List<String> = emptyList(),
    val performanceScore: Double = 0.0
)

// ==========================================
// 6. Attendance & Telemetry (Web Mapped)
// ==========================================

@IgnoreExtraProperties
data class AttendanceEntry(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String? = null,
    val role: String = "student",
    val status: String = "present", // "present" | "late" | "absent" | "excused"
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val durationMinutes: Int = 0,
    val location: String? = null,
    val method: String? = "self_clock",
    val notes: String? = null
)

@IgnoreExtraProperties
data class InstructorEngagement(
    val instructorId: String = "",
    val instructorName: String = "",
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val durationMinutes: Int = 0,
    val status: String = "present"
)

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val courseId: String = "",
    val courseTitle: String? = null,
    val orgId: String? = null,
    val date: String = "",
    val mode: String? = "physical", // "physical" | "online"
    val sessionTitle: String? = null,
    val location: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val expectedDurationMinutes: Int? = null,
    val totalAttendees: Int = 0,
    val avgEngagementMinutes: Int? = null,
    val entries: List<AttendanceEntry> = emptyList(),
    val records: Map<String, Boolean> = emptyMap(),
    val instructorEngagement: InstructorEngagement? = null,
    val instructorEngagementName: String? = null,
    val instructorCheckInTime: String? = null,
    val instructorCheckOutTime: String? = null,
    val instructorDurationMinutes: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val ownerDocId: String? = null
)

// ==========================================
// 7. Schedule Events
// ==========================================

@IgnoreExtraProperties
data class ScheduleEvent(
    @DocumentId val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val date: String = "", // YYYY-MM-DD
    val time: String = "", // HH:MM
    val durationMins: Int = 60,
    val type: String = "lecture", // "lecture" | "meeting" | "exam"
    val meetingUrl: String? = null,
    val isActive: Boolean = false,
    val isCheckedIn: Boolean = false
)

// ==========================================
// 8. Notifications & Discussions (Web Mapped)
// ==========================================

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String? = null,
    val courseId: String? = null,
    val title: String = "",
    val message: String = "",
    val type: String = "info",
    val read: Boolean = false,
    val createdAt: String = "",
    val linkUrl: String? = null
)

@IgnoreExtraProperties
data class DiscussionResource(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val type: String = "link" // "link" | "document" | "syllabus"
)

@IgnoreExtraProperties
data class DiscussionChannel(
    val id: String = "",
    val courseId: String = "",
    val name: String = "",
    val description: String? = null,
    val pinned: Boolean = false,
    val order: Int = 0,
    val createdAt: String = "",
    val resources: List<DiscussionResource> = emptyList(),
    val subscriberIds: List<String> = emptyList()
)

@IgnoreExtraProperties
data class DiscussionLink(
    val label: String = "",
    val url: String = ""
)

@IgnoreExtraProperties
data class DiscussionMessage(
    val id: String = "",
    val channelId: String = "",
    val courseId: String = "",
    val parentId: String? = null,
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String? = "student",
    val text: String = "",
    val fileUrl: String? = null,
    val fileType: String? = null, // "image" | "video" | "document"
    val reactions: Map<String, List<String>> = emptyMap(),
    val verified: Boolean = false,
    val replyCount: Int = 0,
    val pinned: Boolean = false,
    val links: List<DiscussionLink> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class DiscussionPollOption(
    val id: String = "",
    val label: String = "",
    val votes: List<String> = emptyList()
)

@IgnoreExtraProperties
data class DiscussionPoll(
    val id: String = "",
    val channelId: String = "",
    val courseId: String = "",
    val question: String = "",
    val options: List<DiscussionPollOption> = emptyList(),
    val closesAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class CoursePresence(
    val id: String = "",
    val courseId: String = "",
    val userId: String = "",
    val userName: String = "",
    val role: String? = "student",
    val lastActiveAt: Long = 0L
)
