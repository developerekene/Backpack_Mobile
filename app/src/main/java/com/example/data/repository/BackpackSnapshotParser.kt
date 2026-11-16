package com.example.data.repository

import com.example.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class BackpackSnapshotData(
    val organizations: List<Organization> = emptyList(),
    val courses: List<CourseItem> = emptyList(),
    val enrollmentRequests: List<EnrollmentRequest> = emptyList(),
    val orgJoinRequests: List<OrgJoinRequest> = emptyList(),
    val courseDonations: List<CourseDonation> = emptyList(),
    val orgMembers: List<OrgMember> = emptyList(),
    val userProgressList: List<UserProgress> = emptyList(),
    val materials: List<CourseMaterial> = emptyList(),
    val attendanceRecords: List<AttendanceRecord> = emptyList(),
    val assessments: List<Assessment> = emptyList(),
    val submissions: List<Submission> = emptyList(),
    val scheduleEvents: List<ScheduleEvent> = emptyList(),
    val discussionChannels: List<DiscussionChannel> = emptyList(),
    val discussionMessages: List<DiscussionMessage> = emptyList(),
    val discussionPolls: List<DiscussionPoll> = emptyList(),
    val coursePresence: List<CoursePresence> = emptyList()
)

object BackpackSnapshotParser {

    fun getUserMap(data: Map<String, Any?>?): Map<String, Any?> {
        if (data == null) return emptyMap()
        val userRaw = data["user"]
        val mapRaw = if (userRaw is List<*>) userRaw.firstOrNull() as? Map<*, *> else userRaw as? Map<*, *>
        return mapRaw?.mapKeys { it.key.toString() } ?: emptyMap()
    }

    fun getCourseMap(userMap: Map<String, Any?>): Map<String, Any?> {
        return (userMap["course"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
    }

    private fun Map<*, *>.str(key: String): String = (this[key] as? String)?.trim().orEmpty()
    private fun Map<*, *>.strOrNull(key: String): String? = (this[key] as? String)?.trim()?.ifBlank { null }
    private fun Map<*, *>.bool(key: String, default: Boolean = false): Boolean = (this[key] as? Boolean) ?: default
    private fun Map<*, *>.numInt(key: String, default: Int = 0): Int = (this[key] as? Number)?.toInt() ?: default
    private fun Map<*, *>.numIntOrNull(key: String): Int? = (this[key] as? Number)?.toInt()
    private fun Map<*, *>.numDouble(key: String, default: Double = 0.0): Double = (this[key] as? Number)?.toDouble() ?: default
    private fun Map<*, *>.numDoubleOrNull(key: String): Double? = (this[key] as? Number)?.toDouble()
    private fun Map<*, *>.numLong(key: String, default: Long = 0L): Long = (this[key] as? Number)?.toLong() ?: default
    private fun Map<*, *>.strList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { it?.toString()?.trim()?.ifBlank { null } } ?: emptyList()

    private fun parsePaystackSubaccount(raw: Any?): PaystackSubaccount? {
        val m = (raw as? Map<*, *>)?.mapKeys { it.key.toString() } ?: return null
        val code = m.str("subaccount_code")
        if (code.isBlank() && m.str("account_number").isBlank()) return null
        return PaystackSubaccount(
            subaccount_code = code,
            business_name = m.str("business_name"),
            bank_code = m.str("bank_code"),
            bank_name = m.strOrNull("bank_name"),
            country = m.strOrNull("country") ?: "NG",
            currency = m.strOrNull("currency") ?: "NGN",
            account_number = m.str("account_number"),
            account_name = m.strOrNull("account_name"),
            percentage_charge = m.numDouble("percentage_charge", 85.0),
            description = m.strOrNull("description"),
            is_verified = m["is_verified"] as? Boolean,
            mode = m.strOrNull("mode") ?: "live",
            updatedAt = m.strOrNull("updatedAt")
        )
    }

    private fun parseAccommodations(raw: Any?): SpecialNeedsAccommodations? {
        val it = (raw as? Map<*, *>)?.mapKeys { e -> e.key.toString() } ?: return null
        return SpecialNeedsAccommodations(
            enabled = it.bool("enabled", false),
            disabilityCategories = it.strList("disabilityCategories"),
            otherCategoryDescription = it.strOrNull("otherCategoryDescription"),
            examTimeMultiplier = it.numDouble("examTimeMultiplier", 1.0),
            preferredFormatting = it.strList("preferredFormatting"),
            medicalNotes = it.strOrNull("medicalNotes"),
            emergencyHealthNotice = it.strOrNull("emergencyHealthNotice"),
            requestedServices = it.strList("requestedServices"),
            allowInstructorVisibility = it["allowInstructorVisibility"] as? Boolean,
            allowReviewerVisibility = it["allowReviewerVisibility"] as? Boolean,
            updatedAt = it.strOrNull("updatedAt")
        )
    }

    fun parseUserProfile(
        uid: String,
        doc: DocumentSnapshot?,
        fallbackName: String = "",
        fallbackEmail: String = ""
    ): UserProfile {
        val data = doc?.data
        val userMap = getUserMap(data)
        val pInfo = (userMap["personalInformation"] as? Map<*, *>)?.mapKeys { it.key.toString() }

        val name = pInfo?.strOrNull("fullname")
            ?: pInfo?.strOrNull("name")
            ?: userMap.strOrNull("fullname")
            ?: userMap.strOrNull("name")
            ?: doc?.getString("name")
            ?: ""
        val email = pInfo?.strOrNull("email")
            ?: userMap.strOrNull("email")
            ?: doc?.getString("email")
            ?: ""
        val roleRaw = pInfo?.strOrNull("role")
            ?: userMap.strOrNull("role")
            ?: doc?.getString("role")
            ?: ""

        val activeEmail = email.ifBlank { fallbackEmail }
        val activeName = name.ifBlank {
            fallbackName.ifBlank {
                activeEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
            }
        }

        val cleanRole = roleRaw.lowercase().trim()
        val isSuperAdminEmail = activeEmail.lowercase() == "ekenehq@gmail.com"
        val isOrganisation = cleanRole.contains("organisation") ||
            cleanRole.contains("organization") ||
            cleanRole.contains("admin") ||
            isSuperAdminEmail
        val formattedRole = when {
            isSuperAdminEmail || cleanRole == "super_admin" -> "super_admin"
            isOrganisation -> "organization"
            cleanRole.contains("instructor") -> "instructor"
            else -> "student"
        }

        val orgId = pInfo?.strOrNull("orgId")
            ?: userMap.strOrNull("orgId")
            ?: (data?.get("orgId") as? String)
        val ownerId = pInfo?.strOrNull("ownerId")
            ?: userMap.strOrNull("ownerId")
            ?: (data?.get("ownerId") as? String)
        val logoUrl = pInfo?.strOrNull("logoUrl")
            ?: userMap.strOrNull("logoUrl")
            ?: doc?.getString("logoUrl")

        val docsRaw = (pInfo?.get("userDocuments") as? List<*>) ?: (userMap["userDocuments"] as? List<*>)
        val userDocuments = docsRaw?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            UserDocument(
                id = m.str("id"),
                title = m.str("title"),
                url = m.str("url"),
                category = m.strOrNull("category") ?: "other",
                uploadedAt = m.str("uploadedAt")
            )
        } ?: emptyList()

        val accommodations = parseAccommodations(pInfo?.get("accommodations") ?: userMap["accommodations"])

        val createdAtRaw = pInfo?.get("createdAt") ?: userMap["createdAt"] ?: data?.get("createdAt")
        val createdAtIso = when (createdAtRaw) {
            is String -> createdAtRaw
            is Timestamp -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.format(createdAtRaw.toDate())
            }
            else -> null
        }

        return UserProfile(
            userId = uid,
            name = activeName,
            displayName = activeName,
            email = activeEmail,
            role = formattedRole,
            bio = pInfo?.strOrNull("bio") ?: userMap.strOrNull("bio"),
            headline = pInfo?.strOrNull("headline") ?: userMap.strOrNull("headline"),
            cvUrl = pInfo?.strOrNull("cvUrl") ?: userMap.strOrNull("cvUrl"),
            orgId = orgId,
            ownerId = ownerId,
            logoUrl = logoUrl,
            institutionName = pInfo?.strOrNull("institutionName") ?: if (formattedRole == "organization") activeName else null,
            description = pInfo?.strOrNull("description") ?: userMap.strOrNull("description"),
            location = pInfo?.strOrNull("location") ?: userMap.strOrNull("location"),
            baseCurrency = pInfo?.strOrNull("baseCurrency") ?: userMap.strOrNull("baseCurrency"),
            orgType = pInfo?.strOrNull("orgType") ?: userMap.strOrNull("orgType"),
            address = pInfo?.strOrNull("address") ?: userMap.strOrNull("address"),
            registrationId = pInfo?.strOrNull("registrationId") ?: userMap.strOrNull("registrationId"),
            isAccredited = pInfo?.bool("isAccredited", false) ?: false,
            accreditingBody = pInfo?.strOrNull("accreditingBody"),
            accreditationStatus = pInfo?.strOrNull("accreditationStatus"),
            accreditationDocUrl = pInfo?.strOrNull("accreditationDocUrl"),
            motto = pInfo?.strOrNull("motto"),
            phone = pInfo?.strOrNull("phone"),
            website = pInfo?.strOrNull("website"),
            plan = pInfo?.strOrNull("plan") ?: userMap.strOrNull("plan"),
            paystackSubaccount = parsePaystackSubaccount(pInfo?.get("paystackSubaccount") ?: userMap["paystackSubaccount"]),
            kycVerified = pInfo?.bool("kycVerified", false) ?: false,
            kycDocumentUrl = pInfo?.strOrNull("kycDocumentUrl"),
            userDocuments = userDocuments,
            accommodations = accommodations,
            isSuspended = pInfo?.bool("isSuspended", false) ?: false,
            createdAtIso = createdAtIso
        )
    }

    fun parseSnapshot(snapshot: QuerySnapshot): BackpackSnapshotData {
        val organizationsMap = linkedMapOf<String, Organization>()
        val coursesMap = linkedMapOf<String, CourseItem>()
        val enrollmentsMap = linkedMapOf<String, EnrollmentRequest>()
        val orgJoinRequestsMap = linkedMapOf<String, OrgJoinRequest>()
        val courseDonationsMap = linkedMapOf<String, CourseDonation>()
        val orgMembersMap = linkedMapOf<String, OrgMember>()
        val progressList = mutableListOf<UserProgress>()
        val materialsMap = linkedMapOf<String, CourseMaterial>()
        val attendanceMap = linkedMapOf<String, AttendanceRecord>()
        val assessmentsMap = linkedMapOf<String, Assessment>()
        val submissionsMap = linkedMapOf<String, Submission>()
        val scheduleEventsMap = linkedMapOf<String, ScheduleEvent>()
        val channelsMap = linkedMapOf<String, DiscussionChannel>()
        val messagesMap = linkedMapOf<String, DiscussionMessage>()
        val pollsMap = linkedMapOf<String, DiscussionPoll>()
        val presenceMap = linkedMapOf<String, CoursePresence>()

        for (doc in snapshot.documents) {
            val userMap = getUserMap(doc.data)
            val pInfo = (userMap["personalInformation"] as? Map<*, *>)?.mapKeys { it.key.toString() }

            if (pInfo != null) {
                val roleStr = pInfo.str("role")
                val hasOrgMarkers = roleStr == "organization" ||
                    pInfo["orgType"] != null ||
                    pInfo["registrationId"] != null ||
                    pInfo["accreditationStatus"] != null ||
                    pInfo.bool("isAccredited", false)
                if (hasOrgMarkers) {
                    val orgId = pInfo.strOrNull("id") ?: doc.id
                    val isAcc = pInfo.bool("isAccredited", false)
                    organizationsMap[orgId] = Organization(
                        id = orgId,
                        name = pInfo.strOrNull("fullname") ?: pInfo.strOrNull("name") ?: "Organization",
                        description = pInfo.str("description"),
                        logoUrl = pInfo.strOrNull("logoUrl"),
                        ownerId = pInfo.strOrNull("ownerId") ?: doc.id,
                        baseCurrency = pInfo.strOrNull("baseCurrency") ?: "USD",
                        location = pInfo.strOrNull("location"),
                        orgType = pInfo.strOrNull("orgType"),
                        kycVerified = pInfo.bool("kycVerified", false),
                        kycDocumentUrl = pInfo.strOrNull("kycDocumentUrl"),
                        address = pInfo.strOrNull("address"),
                        registrationId = pInfo.strOrNull("registrationId"),
                        isAccredited = isAcc,
                        accreditingBody = pInfo.strOrNull("accreditingBody"),
                        accreditationStatus = pInfo.strOrNull("accreditationStatus")
                            ?: if (isAcc) "accredited" else "unaccredited",
                        accreditationDocUrl = pInfo.strOrNull("accreditationDocUrl"),
                        motto = pInfo.strOrNull("motto"),
                        phone = pInfo.strOrNull("phone"),
                        website = pInfo.strOrNull("website"),
                        plan = pInfo.strOrNull("plan") ?: "free",
                        paystackSubaccount = parsePaystackSubaccount(pInfo["paystackSubaccount"]),
                        isDeleted = pInfo.bool("isDeleted", false)
                    )
                }
            }

            (userMap["enrollmentRequests"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isNotBlank()) {
                    val docsMap = (m["documents"] as? Map<*, *>)?.entries
                        ?.mapNotNull { e ->
                            val k = e.key?.toString()?.trim()?.ifBlank { null } ?: return@mapNotNull null
                            val v = e.value?.toString()?.trim()?.ifBlank { null } ?: return@mapNotNull null
                            k to v
                        }?.toMap() ?: emptyMap()

                    val addDocs = (m["additionalDocuments"] as? List<*>)?.mapNotNull { dr ->
                        val dm = dr as? Map<*, *> ?: return@mapNotNull null
                        val u = dm.str("url")
                        if (u.isBlank()) return@mapNotNull null
                        AdditionalDocument(
                            id = dm.str("id"),
                            name = dm.strOrNull("name") ?: "Attachment",
                            url = u
                        )
                    } ?: emptyList()

                    val history = (m["reapplicationHistory"] as? List<*>)?.mapNotNull { hr ->
                        val hm = hr as? Map<*, *> ?: return@mapNotNull null
                        ReapplicationRecord(
                            id = hm.str("id"),
                            sessionId = hm.strOrNull("sessionId"),
                            sessionName = hm.strOrNull("sessionName"),
                            appliedAt = hm.str("appliedAt"),
                            status = hm.strOrNull("status") ?: "rejected",
                            rejectedAt = hm.strOrNull("rejectedAt"),
                            rejectionReason = hm.strOrNull("rejectionReason")
                        )
                    } ?: emptyList()

                    enrollmentsMap[id] = EnrollmentRequest(
                        id = id,
                        userId = m.str("userId"),
                        orgId = m.strOrNull("orgId") ?: doc.id,
                        userName = m.str("userName"),
                        userEmail = m.strOrNull("userEmail"),
                        courseId = m.str("courseId"),
                        courseTitle = m.str("courseTitle"),
                        sessionId = m.strOrNull("sessionId"),
                        sessionName = m.strOrNull("sessionName"),
                        status = m.strOrNull("status") ?: "pending",
                        paymentStatus = m.strOrNull("paymentStatus"),
                        paymentMethod = m.strOrNull("paymentMethod"),
                        documents = docsMap,
                        additionalDocuments = addDocs,
                        studentNotes = m.strOrNull("studentNotes"),
                        rejectionReason = m.strOrNull("rejectionReason"),
                        rejectedAt = m.strOrNull("rejectedAt"),
                        rejectedSessionId = m.strOrNull("rejectedSessionId"),
                        reapplicationHistory = history,
                        isSponsored = m.bool("isSponsored", false),
                        sponsorName = m.strOrNull("sponsorName"),
                        sponsorEmail = m.strOrNull("sponsorEmail"),
                        accommodations = parseAccommodations(m["accommodations"]),
                        appliedAt = m.str("appliedAt")
                    )
                }
            }

            (userMap["orgJoinRequests"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isNotBlank()) {
                    orgJoinRequestsMap[id] = OrgJoinRequest(
                        id = id,
                        userId = m.str("userId"),
                        orgId = m.str("orgId"),
                        userName = m.str("userName"),
                        orgName = m.str("orgName"),
                        status = m.strOrNull("status") ?: "pending"
                    )
                }
            }

            (userMap["courseDonations"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isNotBlank()) {
                    courseDonationsMap[id] = CourseDonation(
                        id = id,
                        courseId = m.str("courseId"),
                        courseTitle = m.strOrNull("courseTitle"),
                        orgId = m.str("orgId"),
                        amount = m.numDouble("amount", 0.0),
                        currency = m.strOrNull("currency") ?: "USD",
                        donorName = m.strOrNull("donorName"),
                        donorEmail = m.str("donorEmail"),
                        isAnonymous = m.bool("isAnonymous", false),
                        donationType = m.strOrNull("donationType") ?: "general",
                        numberOfStudents = m.numIntOrNull("numberOfStudents"),
                        createdAt = m.str("createdAt")
                    )
                }
            }

            val courseMap = getCourseMap(userMap)
            if (courseMap.isEmpty()) continue

            (courseMap["courses"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                val courseOrgId = m.strOrNull("orgId")
                    ?: userMap.strOrNull("orgId")
                    ?: userMap.strOrNull("ownerId")
                    ?: doc.id

                val modules = (m["modules"] as? List<*>)?.mapNotNull { modRaw ->
                    val mm = modRaw as? Map<*, *> ?: return@mapNotNull null
                    val items = (mm["items"] as? List<*>)?.mapNotNull { itemRaw ->
                        val im = itemRaw as? Map<*, *> ?: return@mapNotNull null
                        CourseModuleItem(
                            id = im.str("id"),
                            title = im.str("title"),
                            type = im.strOrNull("type") ?: "text",
                            description = im.strOrNull("description"),
                            content = im.strOrNull("content"),
                            url = im.strOrNull("url"),
                            videoUrl = im.strOrNull("videoUrl"),
                            documentUrl = im.strOrNull("documentUrl"),
                            audioUrl = im.strOrNull("audioUrl"),
                            pictureUrl = im.strOrNull("pictureUrl"),
                            embedUrl = im.strOrNull("embedUrl"),
                            duration = im.strOrNull("duration")
                        )
                    } ?: emptyList()
                    CourseModule(
                        id = mm.str("id"),
                        title = mm.str("title"),
                        description = mm.str("description"),
                        content = mm.str("content"),
                        items = items,
                        media = mm.strList("media")
                    )
                } ?: emptyList()

                val certMap = m["certificateConfig"] as? Map<*, *>
                val certConfig = certMap?.let {
                    CertificateConfig(
                        enabled = it.bool("enabled", false),
                        customText = it.strOrNull("customText") ?: "Certificate of Completion",
                        orgName = it.strOrNull("orgName"),
                        gradeLevel = it.strOrNull("gradeLevel"),
                        qualificationTitle = it.strOrNull("qualificationTitle"),
                        signatureUrl = it.strOrNull("signatureUrl"),
                        authorizedSealUrl = it.strOrNull("authorizedSealUrl"),
                        themeColor = it.strOrNull("themeColor") ?: "#0078D4",
                        textColor = it.strOrNull("textColor") ?: "#0F172A",
                        layout = it.strOrNull("layout") ?: "classic"
                    )
                }

                coursesMap[id] = CourseItem(
                    id = id,
                    orgId = courseOrgId,
                    createdBy = m.strOrNull("createdBy"),
                    title = m.str("title"),
                    subtitle = m.strOrNull("subtitle"),
                    description = m.str("description"),
                    coverImageUrl = m.strOrNull("coverImageUrl"),
                    price = m.numDouble("price", 0.0),
                    currency = m.strOrNull("currency") ?: "USD",
                    qualificationTitle = m.strOrNull("qualificationTitle"),
                    qualificationType = m.strOrNull("qualificationType"),
                    instructorName = m.strOrNull("instructorName"),
                    instructorId = m.strOrNull("instructorId"),
                    instructors = m.strList("instructors"),
                    pacing = m.strOrNull("pacing") ?: "self-paced",
                    durationWeeks = m.numIntOrNull("durationWeeks"),
                    totalHours = m.numIntOrNull("totalHours"),
                    language = m.strOrNull("language"),
                    requirements = m.strOrNull("requirements"),
                    applicationProcess = m.strOrNull("applicationProcess"),
                    instructorRequirements = m.strOrNull("instructorRequirements"),
                    admissionStatus = m.strOrNull("admissionStatus") ?: "open",
                    activeSessionId = m.strOrNull("activeSessionId"),
                    activeSessionName = m.strOrNull("activeSessionName"),
                    fundingModel = m.strOrNull("fundingModel"),
                    tuitionCostPerStudent = m.numDoubleOrNull("tuitionCostPerStudent"),
                    totalDonationsReceived = m.numDoubleOrNull("totalDonationsReceived"),
                    paymentTermsAllowed = m.strList("paymentTermsAllowed"),
                    enrolledCount = m.numInt("enrolledCount", 0),
                    modules = modules,
                    certificateConfig = certConfig
                )
            }

            (courseMap["orgMembers"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isNotBlank()) {
                    orgMembersMap[id] = OrgMember(
                        id = id,
                        userId = m.strOrNull("userId"),
                        orgId = m.str("orgId"),
                        name = m.str("name"),
                        email = m.str("email"),
                        department = m.strOrNull("department"),
                        role = m.strOrNull("role") ?: "student",
                        status = m.strOrNull("status") ?: "invited",
                        joinedAt = m.str("joinedAt"),
                        courseIds = m.strList("courseIds"),
                        inviteNote = m.strOrNull("inviteNote"),
                        requiresPayment = m.bool("requiresPayment", false),
                        requiresDocuments = m.bool("requiresDocuments", false),
                        requiredDocNames = m.strList("requiredDocNames")
                    )
                }
            }

            (courseMap["userProgress"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val uId = m.strOrNull("userId") ?: doc.id
                val cId = m.str("courseId")
                if (cId.isNotBlank()) {
                    progressList.add(
                        UserProgress(
                            id = m.strOrNull("id"),
                            userId = uId,
                            courseId = cId,
                            completedModuleIds = m.strList("completedModuleIds"),
                            completedItemIds = m.strList("completedItemIds"),
                            performanceScore = m.numDouble("performanceScore", 0.0)
                        )
                    )
                }
            }

            (courseMap["materials"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isNotBlank()) {
                    materialsMap[id] = CourseMaterial(
                        id = id,
                        courseId = m.str("courseId"),
                        title = m.str("title"),
                        url = m.str("url"),
                        type = m.strOrNull("type") ?: "link",
                        content = m.strOrNull("content"),
                        createdAt = m.str("createdAt")
                    )
                }
            }

            (courseMap["attendance"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                val entries = (m["entries"] as? List<*>)?.mapNotNull { er ->
                    val em = er as? Map<*, *> ?: return@mapNotNull null
                    AttendanceEntry(
                        id = em.str("id"),
                        userId = em.str("userId"),
                        userName = em.str("userName"),
                        userEmail = em.strOrNull("userEmail"),
                        role = em.strOrNull("role") ?: "student",
                        status = em.strOrNull("status") ?: "present",
                        checkInTime = em.strOrNull("checkInTime"),
                        checkOutTime = em.strOrNull("checkOutTime"),
                        durationMinutes = em.numInt("durationMinutes", 0),
                        location = em.strOrNull("location"),
                        method = em.strOrNull("method") ?: "self_clock",
                        notes = em.strOrNull("notes")
                    )
                } ?: emptyList()

                val recordsMap = (m["records"] as? Map<*, *>)?.entries
                    ?.associate { it.key.toString() to (it.value as? Boolean == true) }
                    ?: emptyMap()
                val instEngMap = m["instructorEngagement"] as? Map<*, *>
                val parsedInstEng = instEngMap?.let {
                    InstructorEngagement(
                        instructorId = it.str("instructorId"),
                        instructorName = it.str("instructorName"),
                        checkInTime = it.strOrNull("checkInTime"),
                        checkOutTime = it.strOrNull("checkOutTime"),
                        durationMinutes = it.numInt("durationMinutes", 0),
                        status = it.strOrNull("status") ?: "present"
                    )
                }

                attendanceMap[id] = AttendanceRecord(
                    id = id,
                    courseId = m.str("courseId"),
                    courseTitle = m.strOrNull("courseTitle"),
                    orgId = m.strOrNull("orgId"),
                    date = m.str("date"),
                    mode = m.strOrNull("mode") ?: "physical",
                    sessionTitle = m.strOrNull("sessionTitle"),
                    location = m.strOrNull("location"),
                    startTime = m.strOrNull("startTime"),
                    endTime = m.strOrNull("endTime"),
                    expectedDurationMinutes = m.numIntOrNull("expectedDurationMinutes"),
                    totalAttendees = m.numInt("totalAttendees", entries.size),
                    avgEngagementMinutes = m.numIntOrNull("avgEngagementMinutes"),
                    entries = entries,
                    records = recordsMap,
                    instructorEngagement = parsedInstEng,
                    instructorEngagementName = parsedInstEng?.instructorName?.ifBlank { null },
                    instructorCheckInTime = parsedInstEng?.checkInTime,
                    instructorCheckOutTime = parsedInstEng?.checkOutTime,
                    instructorDurationMinutes = parsedInstEng?.durationMinutes,
                    createdAt = m.strOrNull("createdAt"),
                    updatedAt = m.strOrNull("updatedAt"),
                    ownerDocId = doc.id
                )
            }

            (courseMap["assessments"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                val questions = (m["questions"] as? List<*>)?.mapNotNull { qr ->
                    val qm = qr as? Map<*, *> ?: return@mapNotNull null
                    val opts = (qm["options"] as? List<*>)?.mapNotNull { oraw ->
                        val om = oraw as? Map<*, *> ?: return@mapNotNull null
                        AssessmentQuestionOption(
                            id = om.str("id"),
                            text = om.str("text"),
                            isCorrect = om.bool("isCorrect", false)
                        )
                    } ?: emptyList()
                    AssessmentQuestionSummary(
                        id = qm.str("id"),
                        prompt = qm.str("prompt"),
                        format = qm.strOrNull("format") ?: "objective",
                        questionType = qm.strOrNull("questionType"),
                        points = qm.numInt("points", 0),
                        options = opts,
                        correctBoolean = qm["correctBoolean"] as? Boolean,
                        rubric = qm.strOrNull("rubric")
                    )
                } ?: emptyList()
                assessmentsMap[id] = Assessment(
                    id = id,
                    courseId = m.str("courseId"),
                    title = m.str("title"),
                    type = m.strOrNull("type") ?: "assignment",
                    subject = m.strOrNull("subject"),
                    gradeLevel = m.strOrNull("gradeLevel"),
                    description = m.strOrNull("description"),
                    attachments = m.strList("attachments"),
                    instructorName = m.strOrNull("instructorName"),
                    dueDate = m.str("dueDate"),
                    startDate = m.strOrNull("startDate"),
                    startTime = m.strOrNull("startTime"),
                    endTime = m.strOrNull("endTime"),
                    durationMinutes = m.numIntOrNull("durationMinutes"),
                    maxScore = m.numInt("maxScore", 100),
                    passingScore = m.numIntOrNull("passingScore"),
                    gradingType = m.strOrNull("gradingType"),
                    weight = m.numDoubleOrNull("weight"),
                    submissionMethod = m.strOrNull("submissionMethod") ?: "online",
                    allowedFileTypes = m.strList("allowedFileTypes"),
                    maxFileSizeMb = m.numIntOrNull("maxFileSizeMb"),
                    allowResubmission = m.bool("allowResubmission", false),
                    requireStudentComments = m.bool("requireStudentComments", false),
                    isGroup = m.bool("isGroup", false),
                    questions = questions
                )
            }

            (courseMap["submissions"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                val answers = (m["answers"] as? Map<*, *>)?.entries
                    ?.associate { it.key.toString() to (it.value?.toString() ?: "") }
                    ?: emptyMap()
                val essayScores = (m["essayScores"] as? Map<*, *>)?.entries
                    ?.mapNotNull { entry ->
                        val v = (entry.value as? Number)?.toDouble() ?: return@mapNotNull null
                        entry.key.toString() to v
                    }?.toMap() ?: emptyMap()
                submissionsMap[id] = Submission(
                    id = id,
                    assessmentId = m.str("assessmentId"),
                    userId = m.strOrNull("userId") ?: doc.id,
                    userName = m.strOrNull("userName"),
                    courseId = m.str("courseId"),
                    submittedAt = m.str("submittedAt"),
                    content = m.str("content"),
                    fileUrl = m.strOrNull("fileUrl"),
                    score = m.numDoubleOrNull("score"),
                    feedback = m.strOrNull("feedback"),
                    status = m.strOrNull("status") ?: "submitted",
                    autoGradedPoints = m.numDoubleOrNull("autoGradedPoints"),
                    autoGradedMax = m.numDoubleOrNull("autoGradedMax"),
                    pendingEssayGrading = m.bool("pendingEssayGrading", false),
                    essayScores = essayScores,
                    answers = answers
                )
            }

            (courseMap["scheduleEvents"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                scheduleEventsMap[id] = ScheduleEvent(
                    id = id,
                    courseId = m.str("courseId"),
                    title = m.str("title"),
                    date = m.str("date"),
                    time = m.str("time"),
                    durationMins = m.numInt("durationMins", 60),
                    type = m.strOrNull("type") ?: "lecture",
                    meetingUrl = m.strOrNull("meetingUrl"),
                    isActive = m.bool("isActive", false)
                )
            }

            (courseMap["discussionChannels"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                val resources = (m["resources"] as? List<*>)?.mapNotNull { rr ->
                    val rm = rr as? Map<*, *> ?: return@mapNotNull null
                    DiscussionResource(
                        id = rm.str("id"),
                        title = rm.strOrNull("title") ?: rm.str("name"),
                        url = rm.str("url"),
                        type = rm.strOrNull("type") ?: "link"
                    )
                } ?: emptyList()
                channelsMap[id] = DiscussionChannel(
                    id = id,
                    courseId = m.str("courseId"),
                    name = m.str("name"),
                    description = m.strOrNull("description"),
                    pinned = m.bool("pinned", false),
                    order = m.numInt("order", 0),
                    createdAt = m.str("createdAt"),
                    resources = resources,
                    subscriberIds = m.strList("subscriberIds")
                )
            }

            (courseMap["discussionMessages"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                val reactions = (m["reactions"] as? Map<*, *>)?.entries
                    ?.associate { entry ->
                        val users = (entry.value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        entry.key.toString() to users
                    } ?: emptyMap()
                val links = (m["links"] as? List<*>)?.mapNotNull { lr ->
                    val lm = lr as? Map<*, *> ?: return@mapNotNull null
                    DiscussionLink(label = lm.str("label"), url = lm.str("url"))
                } ?: emptyList()
                messagesMap[id] = DiscussionMessage(
                    id = id,
                    channelId = m.str("channelId"),
                    courseId = m.str("courseId"),
                    parentId = m.strOrNull("parentId"),
                    senderId = m.str("senderId"),
                    senderName = m.strOrNull("senderName") ?: "User",
                    senderRole = m.strOrNull("senderRole") ?: "student",
                    text = m.str("text"),
                    fileUrl = m.strOrNull("fileUrl"),
                    fileType = m.strOrNull("fileType"),
                    reactions = reactions,
                    verified = m.bool("verified", false),
                    replyCount = m.numInt("replyCount", 0),
                    pinned = m.bool("pinned", false),
                    links = links,
                    createdAt = m.numLong("createdAt", System.currentTimeMillis())
                )
            }

            (courseMap["discussionPolls"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                val options = (m["options"] as? List<*>)?.mapNotNull { oraw ->
                    val om = oraw as? Map<*, *> ?: return@mapNotNull null
                    DiscussionPollOption(
                        id = om.str("id"),
                        label = om.str("label"),
                        votes = om.strList("votes")
                    )
                } ?: emptyList()
                pollsMap[id] = DiscussionPoll(
                    id = id,
                    channelId = m.str("channelId"),
                    courseId = m.str("courseId"),
                    question = m.str("question"),
                    options = options,
                    closesAt = m.numLong("closesAt", 0L),
                    createdAt = m.numLong("createdAt", System.currentTimeMillis())
                )
            }

            (courseMap["coursePresence"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val id = m.str("id")
                if (id.isBlank()) return@forEach
                presenceMap[id] = CoursePresence(
                    id = id,
                    courseId = m.str("courseId"),
                    userId = m.str("userId"),
                    userName = m.str("userName"),
                    role = m.strOrNull("role"),
                    lastActiveAt = m.numLong("lastActiveAt", 0L)
                )
            }
        }

        return BackpackSnapshotData(
            organizations = organizationsMap.values.toList(),
            courses = coursesMap.values.toList(),
            enrollmentRequests = enrollmentsMap.values.toList(),
            orgJoinRequests = orgJoinRequestsMap.values.toList(),
            courseDonations = courseDonationsMap.values.toList(),
            orgMembers = orgMembersMap.values.toList(),
            userProgressList = progressList,
            materials = materialsMap.values.toList(),
            attendanceRecords = attendanceMap.values.toList(),
            assessments = assessmentsMap.values.toList(),
            submissions = submissionsMap.values.toList(),
            scheduleEvents = scheduleEventsMap.values.toList(),
            discussionChannels = channelsMap.values.toList(),
            discussionMessages = messagesMap.values.toList(),
            discussionPolls = pollsMap.values.toList(),
            coursePresence = presenceMap.values.toList()
        )
    }

    fun resolveOrganizationForUser(
        snapshotData: BackpackSnapshotData,
        uid: String
    ): Organization? {
        return snapshotData.organizations.firstOrNull { o ->
            o.id == uid || o.ownerId == uid || o.id == "org_$uid"
        }
    }

    fun matchesOrganization(
        orgIdCandidate: String?,
        uid: String,
        myOrg: Organization?
    ): Boolean {
        if (orgIdCandidate.isNullOrBlank()) return false
        if (orgIdCandidate == uid || orgIdCandidate == "org_$uid") return true
        if (myOrg != null && (orgIdCandidate == myOrg.id || orgIdCandidate == myOrg.ownerId || orgIdCandidate == "org_${myOrg.id}")) {
            return true
        }
        return false
    }

    fun filterCoursesForRole(
        snapshotData: BackpackSnapshotData,
        profile: UserProfile,
        uid: String
    ): List<CourseItem> {
        val role = profile.userRole
        val email = profile.email.lowercase().trim()
        return when (role) {
            UserRole.STUDENT -> filterEnrolledCoursesForStudent(snapshotData, uid, profile.email)
            UserRole.ORGANISATION -> {
                val myOrg = resolveOrganizationForUser(snapshotData, uid)
                snapshotData.courses.filter { c ->
                    matchesOrganization(c.orgId, uid, myOrg) || c.createdBy == uid
                }
            }
            UserRole.INSTRUCTOR -> {
                val assignedCourseIds = snapshotData.orgMembers
                    .filter { m ->
                        (m.role == "instructor" || m.role.isBlank()) &&
                            ((email.isNotBlank() && m.email.lowercase().trim() == email) ||
                                m.userId == uid ||
                                m.id == uid)
                    }
                    .flatMap { it.courseIds }
                    .toSet()
                snapshotData.courses.filter { c ->
                    c.id in assignedCourseIds ||
                        c.instructorId == uid ||
                        uid in c.instructors ||
                        c.createdBy == uid ||
                        (!c.instructorName.isNullOrBlank() && profile.name.isNotBlank() &&
                            c.instructorName.trim().equals(profile.name.trim(), ignoreCase = true))
                }
            }
            UserRole.SUPER_ADMIN -> snapshotData.courses
        }
    }

    fun filterEnrolledCoursesForStudent(
        snapshotData: BackpackSnapshotData,
        studentUid: String,
        studentEmail: String
    ): List<CourseItem> {
        val orgsById = snapshotData.organizations.associateBy { it.id }
        val normalizedEmail = studentEmail.lowercase().trim()

        val activeMemberCourseIds = snapshotData.orgMembers
            .filter { m ->
                (m.email.lowercase().trim() == normalizedEmail && normalizedEmail.isNotBlank()) ||
                    m.userId == studentUid ||
                    m.id == studentUid
            }
            .filter { it.role == "student" && it.status == "active" }
            .flatMap { it.courseIds }
            .toSet()

        val approvedEnrollments = snapshotData.enrollmentRequests
            .filter { it.userId == studentUid && it.status == "approved" }
            .associateBy { it.courseId }

        return snapshotData.courses.filter { course ->
            if (course.id in activeMemberCourseIds) return@filter true
            val req = approvedEnrollments[course.id] ?: return@filter false
            val courseOrg = orgsById[course.orgId]
                ?: snapshotData.organizations.firstOrNull { it.ownerId == course.orgId }
            val isDonationFunded = courseOrg?.orgType == "vocational" &&
                course.fundingModel == "donations_sponsorships"
            val isPaidOrFree = course.price <= 0.0 ||
                req.paymentStatus == "paid" ||
                req.isSponsored ||
                isDonationFunded ||
                course.fundingModel == "donations_sponsorships"
            isPaidOrFree
        }
    }

    fun computeVocationalAdmissionGate(
        course: CourseItem?,
        organizations: List<Organization>,
        courseDonations: List<CourseDonation>,
        enrollmentRequests: List<EnrollmentRequest>
    ): VocationalAdmissionGate? {
        if (course == null) return null
        val org = organizations.firstOrNull { it.id == course.orgId || it.ownerId == course.orgId || "org_${it.ownerId}" == course.orgId }
        val isVocational = org?.orgType == "vocational"
        val isDonationFunded = course.fundingModel == "donations_sponsorships"
        if (!isVocational || !isDonationFunded) return null

        val tuitionCostPerStudent = (course.tuitionCostPerStudent ?: course.price).coerceAtLeast(0.0)
        val currency = course.currency.ifBlank { "USD" }
        val totalDonations = course.totalDonationsReceived
            ?: courseDonations.filter { it.courseId == course.id }.sumOf { it.amount }
        val currentlyAdmitted = enrollmentRequests.count { it.courseId == course.id && it.status == "approved" }

        var maxAdmissible = -1
        var remainingSpots = Int.MAX_VALUE
        var canAdmitMore = true
        var nextSeatNeeded = 0.0

        if (tuitionCostPerStudent > 0.0) {
            maxAdmissible = kotlin.math.floor(totalDonations / tuitionCostPerStudent).toInt()
            remainingSpots = (maxAdmissible - currentlyAdmitted).coerceAtLeast(0)
            canAdmitMore = remainingSpots > 0
            val nextSeatFunds = (currentlyAdmitted + 1) * tuitionCostPerStudent
            nextSeatNeeded = (nextSeatFunds - totalDonations).coerceAtLeast(0.0)
        }

        return VocationalAdmissionGate(
            isVocational = isVocational,
            isDonationFunded = isDonationFunded,
            tuitionCostPerStudent = tuitionCostPerStudent,
            totalDonations = totalDonations,
            maxAdmissibleStudents = maxAdmissible,
            currentlyAdmittedCount = currentlyAdmitted,
            remainingSpots = remainingSpots,
            canAdmitMore = canAdmitMore,
            currency = currency,
            nextSeatNeededAmount = nextSeatNeeded
        )
    }
}
