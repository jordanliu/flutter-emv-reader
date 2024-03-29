import 'package:flutter/foundation.dart';

@immutable
class EmvCard {
  final String? number;

  final String? expire;

  final String? holder;

  final String? type;

  final String? status;

  final String? track2;

  const EmvCard(
      {this.number,
      this.expire,
      this.holder,
      this.type,
      this.status,
      this.track2});
}
