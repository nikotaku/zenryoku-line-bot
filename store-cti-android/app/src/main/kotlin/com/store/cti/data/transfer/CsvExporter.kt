package com.store.cti.data.transfer

import android.content.Context
import android.net.Uri
import com.store.cti.core.csv.CsvWriter
import com.store.cti.core.model.CallDirection
import com.store.cti.core.model.CallResult
import com.store.cti.core.model.CustomerCategory
import com.store.cti.core.model.ReservationChannel
import com.store.cti.core.model.ReservationStatus
import com.store.cti.core.model.SmsDraftStatus
import com.store.cti.data.local.AppDatabase
import com.store.cti.data.repository.AuditLogger
import com.store.cti.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** CSV出力の対象 */
enum class CsvTarget(val label: String, val fileNamePrefix: String) {
    CUSTOMERS("顧客一覧", "customers"),
    RESERVATIONS("予約一覧", "reservations"),
    INTERACTIONS("対応履歴", "call_interactions"),
    SMS_DRAFTS("SMS作成履歴", "sms_drafts"),
}

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val auditLogger: AuditLogger,
) {
    /** 対象データのCSV文字列(UTF-8 BOM付き・CRLF)を生成する */
    suspend fun buildCsv(target: CsvTarget): String = when (target) {
        CsvTarget.CUSTOMERS -> buildCustomersCsv()
        CsvTarget.RESERVATIONS -> buildReservationsCsv()
        CsvTarget.INTERACTIONS -> buildInteractionsCsv()
        CsvTarget.SMS_DRAFTS -> buildSmsDraftsCsv()
    }

    suspend fun exportTo(target: CsvTarget, uri: Uri): TransferOutcome = withContext(Dispatchers.IO) {
        try {
            val csv = buildCsv(target)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(csv.toByteArray(Charsets.UTF_8))
            } ?: return@withContext TransferOutcome.Failure("保存先を開けませんでした")
            auditLogger.log(AuditLogger.ACTION_EXPORT_CSV, AuditLogger.TARGET_ALL, detail = target.name)
            TransferOutcome.Success("${target.label}のCSVを保存しました")
        } catch (t: Throwable) {
            AppLogger.e("csv export failed", t)
            TransferOutcome.Failure("CSVの出力に失敗しました。空き容量と保存先を確認してください")
        }
    }

    private suspend fun buildCustomersCsv(): String {
        val rows = database.customerDao().observeActive().first().map { c ->
            listOf(
                c.id, c.customerNumber, c.name, c.kana,
                c.displayPhoneNumber, c.displayPhoneNumber2, c.lineName,
                c.firstVisitDate ?: "", c.lastVisitDate ?: "", c.visitCount.toString(),
                c.lastStaffName, c.preferredStaffName,
                CustomerCategory.fromName(c.category).label,
                c.cautionNotes, c.servicePreferences, c.prohibitedActions, c.memo,
            )
        }
        return CsvWriter.buildCsv(
            listOf(
                "顧客ID", "顧客番号", "氏名", "ふりがな", "電話番号", "予備電話番号", "LINE名",
                "初回来店日", "最終来店日", "利用回数", "前回担当者", "希望担当者",
                "顧客区分", "注意事項", "接客上の好み", "禁止事項", "メモ",
            ),
            rows,
        )
    }

    private suspend fun buildReservationsCsv(): String {
        val customers = database.customerDao().observeActive().first().associateBy { it.id }
        val rows = database.reservationDao().observeActive().first().map { r ->
            listOf(
                r.id, r.date, r.startTime, r.endTime,
                r.customerId?.let { customers[it]?.name } ?: "",
                r.customerId?.let { customers[it]?.displayPhoneNumber } ?: "",
                r.staffName, r.courseName, r.price?.toString() ?: "",
                ReservationChannel.fromName(r.channel).label,
                ReservationStatus.fromName(r.status).label,
                r.room, r.requests, r.cautionNotes, r.createdByStaffName,
            )
        }
        return CsvWriter.buildCsv(
            listOf(
                "予約ID", "予約日", "開始時間", "終了時間", "顧客名", "電話番号",
                "担当者", "コース", "料金", "予約経路", "予約状態", "部屋", "要望", "注意事項", "登録スタッフ",
            ),
            rows,
        )
    }

    private suspend fun buildInteractionsCsv(): String {
        val customers = database.customerDao().observeActive().first().associateBy { it.id }
        val rows = database.callInteractionDao().observeActive().first().map { i ->
            listOf(
                i.id, i.occurredAt,
                CallDirection.fromName(i.direction).label,
                if (i.answered) "出た" else "出ていない",
                CallResult.fromName(i.result).label,
                i.customerId?.let { customers[it]?.name } ?: "",
                i.displayPhoneNumber, i.inquiry, i.desiredDateTime, i.desiredStaffName,
                i.guidanceGiven, if (i.reservationCreated) "あり" else "なし",
                if (i.callbackRequired) "要" else "不要",
                if (i.callbackDone) "完了" else "",
                i.nextActionAt ?: "", i.staffName, i.memo,
            )
        }
        return CsvWriter.buildCsv(
            listOf(
                "対応ID", "対応日時", "着信/発信", "応答", "対応結果", "顧客名", "電話番号",
                "問い合わせ内容", "希望日時", "希望担当者", "案内した内容", "予約成立",
                "折り返し要否", "折り返し完了", "次回対応予定", "スタッフ名", "メモ",
            ),
            rows,
        )
    }

    private suspend fun buildSmsDraftsCsv(): String {
        val customers = database.customerDao().observeActive().first().associateBy { it.id }
        val rows = database.smsDraftDao().observeActive().first().map { d ->
            listOf(
                d.id,
                d.customerId?.let { customers[it]?.name } ?: "",
                d.displayPhoneNumber, d.templateName, d.body,
                SmsDraftStatus.fromName(d.status).label, d.staffName,
            )
        }
        return CsvWriter.buildCsv(
            listOf("ID", "顧客名", "宛先電話番号", "テンプレート", "本文", "状態", "スタッフ名"),
            rows,
        )
    }
}
