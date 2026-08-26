import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import 'core_card.dart';

class CoreDataColumn {
  final String label;
  final bool numeric;

  const CoreDataColumn({
    required this.label,
    this.numeric = false,
  });
}

class CoreDataRow {
  final List<Widget> cells;
  final VoidCallback? onTap;

  const CoreDataRow({
    required this.cells,
    this.onTap,
  });
}

/// A responsive, animated data table matching the Cognitive Trust design system.
class CoreDataTable extends StatelessWidget {
  const CoreDataTable({
    super.key,
    required this.columns,
    required this.rows,
  });

  final List<CoreDataColumn> columns;
  final List<CoreDataRow> rows;

  @override
  Widget build(BuildContext context) {
    return CoreCard(
      elevation: 1,
      padding: EdgeInsets.zero,
      child: LayoutBuilder(
        builder: (context, constraints) {
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: ConstrainedBox(
              constraints: BoxConstraints(minWidth: constraints.maxWidth),
              child: DataTable(
                headingRowColor: WidgetStateProperty.all(AppColors.surfaceContainerLow),
                dataRowColor: WidgetStateProperty.resolveWith<Color>((Set<WidgetState> states) {
                  if (states.contains(WidgetState.hovered)) {
                    return AppColors.surfaceContainerHighest.withValues(alpha: 0.5);
                  }
                  return AppColors.surfaceContainerLowest;
                }),
                headingTextStyle: AppTypography.labelSmall.copyWith(
                  color: AppColors.outline,
                  letterSpacing: 1.2,
                ),
                dataTextStyle: AppTypography.bodyMedium.copyWith(
                  color: AppColors.onSurface,
                ),
                dividerThickness: 1,
                columnSpacing: 24,
                horizontalMargin: 24,
                columns: columns.map((col) {
                  return DataColumn(
                    label: Text(col.label.toUpperCase()),
                    numeric: col.numeric,
                  );
                }).toList(),
                rows: rows.map((row) {
                  return DataRow(
                    cells: row.cells.map((cell) => DataCell(cell)).toList(),
                    onSelectChanged: row.onTap != null
                        ? (_) => row.onTap!()
                        : null,
                  );
                }).toList(),
              ),
            ),
          );
        },
      ),
    );
  }
}
