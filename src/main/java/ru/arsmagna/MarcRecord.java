// This is an open source non-commercial project. Dear PVS-Studio, please check it.
// PVS-Studio Static Code Analyzer for C, C++ and C#: http://www.viva64.com

package ru.arsmagna;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static ru.arsmagna.Utility.isNullOrEmpty;
import static ru.arsmagna.Utility.nullableToString;

/**
 * MARC record.
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue", "unused"})
public final class MarcRecord implements Cloneable {
    /**
     * Database that contains the record.
     */
    public String database;

    /**
     * Master file number.
     */
    public int mfn;

    /**
     * The record status.
     */
    public int status;

    /**
     * Версия записи. Нумеруется с нуля.
     */
    public int version;

    /**
     * Смещение предыдущей версии записи в MST-файле.
     */
    public long previousOffset;

    public final Collection<RecordField> fields = new ArrayList<>();

    /**
     * Библиографическое описание.
     */
    public String description;

    /**
     * Используется при сортировке записей.
     */
    public String sortKey;

    /**
     * Индекс документа.
     */
    public String index;

    /**
     * Произвольные пользовательские данные.
     */
    @SuppressFBWarnings("UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD")
    public Object userData;

    //=========================================================================

//    /**
//     * Default constructor.
//     */
//    public MarcRecord() {
//    }

    //=========================================================================

    public MarcRecord addField(int tag, Object value) {
        RecordField field = new RecordField(tag, nullableToString(value));
        fields.add(field);

        return this;
    }

    public MarcRecord addField(int tag, boolean condition, Object value) {
        if (condition) {
            RecordField field = new RecordField(tag, nullableToString(value));
            fields.add(field);
        }

        return this;
    }

    public MarcRecord addField(int tag, SubField... subFields) {
        RecordField field = new RecordField(tag);
        field.subFields.addAll(Arrays.asList(subFields));
        fields.add(field);

        return this;
    }

    public MarcRecord addField(int tag, boolean condition, SubField... subFields) {
        if (condition) {
            RecordField field = new RecordField(tag);
            field.subFields.addAll(Arrays.asList(subFields));
            fields.add(field);
        }

        return this;
    }

    public MarcRecord addNonEmptyField(int tag, Object value) {
        String text = nullableToString(value);
        if (!isNullOrEmpty(text)) {
            RecordField field = new RecordField(tag, text);
            fields.add(field);
        }

        return this;
    }

    public boolean removeField(RecordField field) {
        return fields.remove(field);
    }

    /**
     * Очистка записи (удаление всех полей).
     *
     * @return Ту же запись.
     */
    public MarcRecord clear() {
        fields.clear();

        return this;
    }

    /**
     * Клонирование записи со всеми полями.
     *
     * @return Копию записи.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public MarcRecord clone() {
        MarcRecord result = new MarcRecord();
        result.database = database;
        result.mfn = mfn;
        result.status = status;
        result.description = description;
        result.sortKey = sortKey;
        result.index = index;
        for (RecordField field : fields) {
            result.fields.add(field.clone());
        }

        return result;
    }

    @Contract(pure = true)
    public Optional<String> fm(int tag) {
        for (RecordField field : fields) {
            if (field.tag == tag) {
                return Optional.of(field.value);
            }
        }

        return Optional.empty();
    }

    @Contract(pure = true)
    public Optional<String> fm(int tag, char code) {
        for (RecordField field : fields) {
            if (field.tag == tag) {
                return Optional.ofNullable(field.getFirstSubFieldValue(code));
            }
        }

        return Optional.empty();
    }

    @NotNull
    public LinkedList<String> fma(int tag) {
        LinkedList<String> result = new LinkedList<>();
        for (RecordField field : fields) {
            if (field.tag == tag) {
                if (!isNullOrEmpty(field.value)) {
                    result.add(field.value);
                }
            }
        }

        return result;
    }

    @NotNull
    public LinkedList<String> fma(int tag, char code) {
        LinkedList<String> result = new LinkedList<>();
        for (RecordField field : fields) {
            if (field.tag == tag) {
                String text = field.getFirstSubFieldValue(code);
                if (!isNullOrEmpty(text)) {
                    result.add(text);
                }
            }
        }

        return result;
    }

    @NotNull
    public LinkedList<RecordField> getField(int tag) {
        LinkedList<RecordField> result = new LinkedList<>();
        for (RecordField field : fields) {
            if (field.tag == tag) {
                result.add(field);
            }
        }

        return result;
    }

    @Contract(pure = true)
    public Optional<RecordField> getField(int tag, int occurrence) {
        for (RecordField field : fields) {
            if (field.tag == tag && --occurrence < 0) {
                return Optional.of(field);
            }
        }

        return Optional.empty();
    }

    @Contract(pure = true)
    public Optional<RecordField> getFirstField(int tag) {
        for (RecordField field : fields) {
            if (field.tag == tag) {
                return Optional.of(field);
            }
        }

        return Optional.empty();
    }

    public Optional<String> getFirstFieldAndSubField(int tag, char subTag) {
        return fields.stream()
                .filter(field -> field.tag == tag)
                .findFirst()
                .flatMap(recordField -> recordField.getFirstSubField(subTag)
                        .map(subField -> subField.value));
    }

    /**
     * Удалена ли запись?
     *
     * @return true, если запись удалена.
     */
    @Contract(pure = true)
    public boolean isDeleted() {
        return (status & (RecordStatus.LOGICALLY_DELETED | RecordStatus.PHYSICALLY_DELETED)) != 0;
    }

    /**
     * Разбор строк, возвращаемых сервером.
     *
     * @param text Строки, содержащие запись.
     * @throws IOException Ошибка ввода-вывода.
     */
    public void parseSingle(@NotNull String[] text) throws IOException {
        if (text.length == 0) {
            return;
        }

        String line = text[0];
        String[] parts = line.split("#");
        mfn = FastNumber.parseInt32(parts[0]);
        if (parts.length != 1) {
            status = FastNumber.parseInt32(parts[1]);
        }
        line = text[1];
        parts = line.split("#");
        version = FastNumber.parseInt32(parts[1]);
        fields.clear();
        for (int i = 2; i < text.length; i++) {
            RecordField field = RecordField.parse(text[i]);
            fields.add(field);
        }
    }

    //=========================================================================

    @Override
    public String toString() {
        return ProtocolText.encodeRecord(this);
    }
}
