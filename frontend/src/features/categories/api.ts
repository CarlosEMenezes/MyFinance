import { getJson, patchJson, postJson } from '../../lib/http';
import type {
  Category,
  CategoryList,
  CreateCategoryRequest,
  PeriodKind,
  UpdateCategoryPlanRequest,
} from '../../types/api';

export function fetchCategories(period: PeriodKind): Promise<CategoryList> {
  return getJson<CategoryList>(`/categories?period=${period}`);
}

export function updateCategoryPlan(
  id: string,
  changes: UpdateCategoryPlanRequest,
): Promise<Category> {
  return patchJson<Category>(`/categories/${id}`, changes);
}

export function createCategory(body: CreateCategoryRequest): Promise<Category> {
  return postJson<Category>('/categories', body);
}
